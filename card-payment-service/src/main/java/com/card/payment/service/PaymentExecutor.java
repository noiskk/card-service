package com.card.payment.service;

import com.card.payment.client.BankClient;
import com.card.payment.dto.PaymentRequest;
import com.card.payment.dto.PaymentResponse;
import com.card.payment.dto.WithdrawRequest;
import com.card.payment.dto.WithdrawResponse;
import com.card.payment.entity.*;
import com.card.payment.exception.CardNotFoundException;
import com.card.payment.exception.DownstreamCallFailedException;
import com.card.payment.exception.InvalidCardTypeException;
import com.card.payment.repository.AuthorizationRepository;
import com.card.payment.repository.CardInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 실제 결제 실행 로직 (카드 조회 → 분기 → 출금 → 승인원장 저장).
 * 멱등성 레이어(PaymentProcessorService)를 통과한 뒤 딱 한 번만 호출된다.
 *
 * 별도 빈으로 분리한 이유: Spring 프록시를 타서 트랜잭션 경계가 살아난다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentExecutor {

    private final CardInfoRepository cardInfoRepository;
    private final AuthorizationRepository authorizationRepository;
    private final BankClient bankClient;

    @Transactional
    public PaymentResponse execute(PaymentRequest request) {
        String transactionId = UUID.randomUUID().toString();
        log.info("결제 처리 시작 - 거래ID: {}, 카드타입: {}", transactionId, request.getCardType());

        // 카드 조회
        Card card = cardInfoRepository.findByCardNumber(request.getCardNum())
                .orElseThrow(() -> new CardNotFoundException(transactionId, request.getCardNum()));

        // 카드 타입에 따라 분기
        CardType cardType;
        try {
            cardType = CardType.valueOf(request.getCardType());
        } catch (IllegalArgumentException e) {
            throw new InvalidCardTypeException(transactionId, request.getCardType());
        }

        if (cardType == CardType.DEBIT) {
            return processDebit(transactionId, card, request);
        } else {
            return processCredit(transactionId, card, request);
        }
    }

    private PaymentResponse processDebit(String transactionId, Card card, PaymentRequest request) {
        log.info("체크카드 결제 처리 - 거래ID: {}", transactionId);

        if (card.getPerTransactionLimit() != null && request.getAmount() > card.getPerTransactionLimit()) {
            log.warn("1회 결제 한도 초과 - 거래ID: {}, 요청금액: {}, 한도: {}",
                    transactionId, request.getAmount(), card.getPerTransactionLimit());
            return saveAndRespond(transactionId, card, request, "61", "1회 결제 한도 초과", false);
        }

        WithdrawResponse withdrawResponse;
        try {
            withdrawResponse = bankClient.withdraw(
                    new WithdrawRequest(request.getCardNum(), request.getAmount()));
        } catch (Exception e) {
            throw new DownstreamCallFailedException(transactionId, request.getAmount(), e);
        }

        if (!withdrawResponse.isSuccess()) {
            log.warn("출금 실패 - 거래ID: {}", transactionId);
            return saveAndRespond(transactionId, card, request, "51", "출금 실패", false);
        }

        log.info("체크카드 결제 성공 - 거래ID: {}", transactionId);
        return saveAndRespond(transactionId, card, request, "00", "결제 성공", true);
    }

    private PaymentResponse processCredit(String transactionId, Card card, PaymentRequest request) {
        log.info("신용카드 결제 처리 - 거래ID: {}", transactionId);

        if (card.getPerTransactionLimit() != null && request.getAmount() > card.getPerTransactionLimit()) {
            log.warn("1회 결제 한도 초과 - 거래ID: {}, 요청금액: {}, 한도: {}",
                    transactionId, request.getAmount(), card.getPerTransactionLimit());
            return saveAndRespond(transactionId, card, request, "61", "1회 결제 한도 초과", false);
        }

        Long creditLimit = card.getCreditLimit();
        Long usedAmount = card.getUsedAmount() != null ? card.getUsedAmount() : 0L;

        if (creditLimit == null || request.getAmount() > (creditLimit - usedAmount)) {
            log.warn("신용 한도 초과 - 거래ID: {}, 요청금액: {}, 잔여한도: {}",
                    transactionId, request.getAmount(),
                    creditLimit != null ? creditLimit - usedAmount : "null");
            return saveAndRespond(transactionId, card, request, "51", "신용 한도 초과", false);
        }

        card.setUsedAmount(usedAmount + request.getAmount());
        cardInfoRepository.save(card);

        log.info("신용카드 결제 성공 - 거래ID: {}, 누적사용액: {}", transactionId, card.getUsedAmount());
        return saveAndRespond(transactionId, card, request, "00", "결제 성공", true);
    }

    private PaymentResponse saveAndRespond(String transactionId, Card card, PaymentRequest request,
                                           String responseCode, String message, boolean success) {
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