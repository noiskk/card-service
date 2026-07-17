package com.card.payment.service;

import com.card.payment.client.BankClient;
import com.card.payment.dto.PaymentRequest;
import com.card.payment.dto.PaymentResponse;
import com.card.payment.dto.WithdrawRequest;
import com.card.payment.dto.WithdrawResponse;
import com.card.payment.entity.*;
import com.card.payment.repository.AuthorizationRepository;
import com.card.payment.repository.CardInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.card.payment.exception.CardNotFoundException;
import com.card.payment.exception.DownstreamCallFailedException;
import com.card.payment.exception.InvalidCardTypeException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessorService {

    private final CardInfoRepository cardInfoRepository;
    private final AuthorizationRepository authorizationRepository;
    private final BankClient bankClient;

    /**
     * 결제 요청 처리 (FDS 통과 후 호출됨)
     * cardType에 따라 체크카드/신용카드 분기
     */
    @Transactional
    public PaymentResponse process(PaymentRequest request) {
        String transactionId = UUID.randomUUID().toString();
        log.info("결제 처리 시작 - 거래ID: {}, 카드타입: {}", transactionId,
                request.getCardType());

        // 카드 조회
        Card card = cardInfoRepository.findByCardNumber(request.getCardNum())
                .orElseThrow(() -> new CardNotFoundException(transactionId, request.getCardNum()));

        // 카드 타입에 따라 분기
        CardType cardType;
        try{
            cardType = CardType.valueOf(request.getCardType());
        } catch (IllegalArgumentException e){
            throw new InvalidCardTypeException(transactionId, request.getCardType());
        }

        PaymentResponse response;
        if (cardType == CardType.DEBIT) {
            response = processDebit(transactionId, card, request);
        } else {
            response = processCredit(transactionId, card, request);
        }

        return response;
    }

    /**
     * 체크카드 결제 처리
     * 1) 1회 결제 한도(perTransactionLimit) 검사
     * 2) 은행 출금 API 호출
     */
    private PaymentResponse processDebit(String transactionId, Card card, PaymentRequest request)
    {
        log.info("체크카드 결제 처리 - 거래ID: {}", transactionId);

        // 1. 1회 결제 한도 확인
        if (card.getPerTransactionLimit() != null && request.getAmount() > card.getPerTransactionLimit()) {
            log.warn("1회 결제 한도 초과 - 거래ID: {}, 요청금액: {}, 한도: {}",
                    transactionId, request.getAmount(), card.getPerTransactionLimit());
            return saveAndRespond(transactionId, card, request, "61", "1회 결제 한도 초과", false);
        }

        // 2. 은행 출금 요청
        WithdrawResponse withdrawResponse;
        try {
            withdrawResponse = bankClient.withdraw(
                    new WithdrawRequest(request.getCardNum(), request.getAmount()));
        } catch (Exception e) {
            // 은행 호출 자체가 실패 - 출금 성공 여부를 알 수 없다.
            // REJECTED로 단정해 기록하지 않고 예외를 전파한다 (3단계 Saga가 다룰 대상).
            throw new DownstreamCallFailedException(transactionId, request.getAmount(), e);
        }

        // 3. 출금 실패 (은행이 명시적으로 거절 - 이건 확실한 결과라 REJECTED 기록해도 됨)
        if (!withdrawResponse.isSuccess()) {
            log.warn("출금 실패 - 거래ID: {}", transactionId);
            return saveAndRespond(transactionId, card, request, "51", "출금 실패",
                    false);
        }

        // 4. 성공
        log.info("체크카드 결제 성공 - 거래ID: {}", transactionId);
        return saveAndRespond(transactionId, card, request, "00", "결제 성공", true);
    }

    /**
     * 신용카드 결제 처리
     * 1) 1회 결제 한도(perTransactionLimit) 검사
     * 2) 신용 잔여 한도(creditLimit - usedAmount) 검사
     */
    private PaymentResponse processCredit(String transactionId, Card card, PaymentRequest request)
    {
        log.info("신용카드 결제 처리 - 거래ID: {}", transactionId);

        // 1. 1회 결제 한도 확인
        if (card.getPerTransactionLimit() != null && request.getAmount() > card.getPerTransactionLimit()) {
            log.warn("1회 결제 한도 초과 - 거래ID: {}, 요청금액: {}, 한도: {}",
                    transactionId, request.getAmount(), card.getPerTransactionLimit());
            return saveAndRespond(transactionId, card, request, "61", "1회 결제 한도 초과", false);
        }

        // 2. 신용 잔여 한도 확인 (creditLimit - usedAmount)
        Long creditLimit = card.getCreditLimit();
        Long usedAmount = card.getUsedAmount() != null ? card.getUsedAmount() : 0L;

        if (creditLimit == null || request.getAmount() > (creditLimit - usedAmount)) {
            log.warn("신용 한도 초과 - 거래ID: {}, 요청금액: {}, 잔여한도: {}",
                    transactionId, request.getAmount(),
                    creditLimit != null ? creditLimit - usedAmount : "null");
            return saveAndRespond(transactionId, card, request, "51", "신용 한도 초과", false);
        }

        // 3. 사용 금액 누적
        card.setUsedAmount(usedAmount + request.getAmount());
        cardInfoRepository.save(card);

        log.info("신용카드 결제 성공 - 거래ID: {}, 누적사용액: {}", transactionId,
                card.getUsedAmount());
        return saveAndRespond(transactionId, card, request, "00", "결제 성공", true);
    }

    /**
     * 승인 이력 저장 + 응답 생성
     */
    private PaymentResponse saveAndRespond(String transactionId, Card card,
                                           PaymentRequest request,
                                           String responseCode, String message, boolean success)
    {
        LocalDateTime now = LocalDateTime.now();

        Authorization authorization = Authorization.builder()
                .transactionId(transactionId)
                .card(card)
                .amount(request.getAmount())
                .responseCode(responseCode)
                .status(success ? AuthorizationStatus.APPROVED : AuthorizationStatus.REJECTED)
                .merchantId(request.getMerchantId())
                .build();

        authorizationRepository.save(authorization);

        return PaymentResponse.builder()
                .transactionId(transactionId)
                .responseCode(responseCode)
                .message(message)
                .amount(request.getAmount())
                .processedAt(now)
                .success(success)
                .build();
    }
}
