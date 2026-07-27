package com.card.payment.service;

import com.card.payment.client.BankClient;
import com.card.payment.client.FdsClient;
import com.card.payment.client.LedgerClient;
import com.card.payment.dto.FdsInspectRequest;
import com.card.payment.dto.FdsInspectResponse;
import com.card.payment.dto.LedgerRecordRequest;
import com.card.payment.dto.PaymentRequest;
import com.card.payment.dto.PaymentResponse;
import com.card.payment.dto.WithdrawRequest;
import com.card.payment.dto.WithdrawResponse;
import com.card.payment.entity.Card;
import com.card.payment.entity.CardType;
import com.card.payment.exception.CardNotFoundException;
import com.card.payment.exception.DownstreamCallFailedException;
import com.card.payment.exception.InvalidCardTypeException;
import com.card.payment.exception.LedgerRecordFailedException;
import com.card.payment.repository.CardInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 승인 오케스트레이션: FDS 판정 → 카드 확인 → (체크카드) 은행 출금 → 원장 기록.
 * 멱등성 레이어(PaymentProcessorService)를 통과한 뒤 딱 한 번만 호출된다.
 *
 * 별도 빈으로 분리한 이유: Spring 프록시를 타서 트랜잭션 경계가 살아난다.
 * 단 은행·FDS·원장은 원격 호출이라 이 트랜잭션의 보호를 받지 못한다
 * → 실패 시 보상/대사는 CompensationService가 담당한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentExecutor {

    private final CardInfoRepository cardInfoRepository;
    private final FdsClient fdsClient;
    private final BankClient bankClient;
    private final LedgerClient ledgerClient;
    private final CompensationService compensationService;

    @Transactional
    public PaymentResponse execute(PaymentRequest request) {
        String transactionId = UUID.randomUUID().toString();
        log.info("승인 처리 시작 - 거래ID: {}, 카드타입: {}", transactionId, request.getCardType());

        // 1. 이상거래 판정 — 차단이면 출금 없이 거절 원장만 남긴다
        FdsInspectResponse verdict = inspect(transactionId, request);
        if (!verdict.isSuccess()) {
            log.warn("FDS 차단 - 거래ID: {}, 코드: {}", transactionId, verdict.getResponseCode());
            return recordAndRespond(transactionId, request.getCardNum(), request,
                    verdict.getResponseCode(), verdict.getMessage(), false);
        }

        // 2. 카드 확인
        Card card = cardInfoRepository.findByCardNumber(request.getCardNum())
                .orElseThrow(() -> new CardNotFoundException(transactionId, request.getCardNum()));

        // 3. 카드 타입은 FDS가 DB에서 확인해준 값을 우선한다(요청값은 신뢰하지 않는다)
        String resolvedType = (verdict.getCardType() != null) ? verdict.getCardType() : request.getCardType();
        CardType cardType;
        try {
            cardType = CardType.valueOf(resolvedType);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidCardTypeException(transactionId, resolvedType);
        }

        return (cardType == CardType.DEBIT)
                ? processDebit(transactionId, card, request)
                : processCredit(transactionId, card, request);
    }

    private FdsInspectResponse inspect(String transactionId, PaymentRequest request) {
        try {
            return fdsClient.inspect(FdsInspectRequest.builder()
                    .cardNum(request.getCardNum())
                    .amount(request.getAmount())
                    .merchantId(request.getMerchantId())
                    .cardType(request.getCardType())
                    .idempotencyKey(request.getIdempotencyKey())
                    .build());
        } catch (Exception e) {
            // FDS가 죽으면 승인 판단 자체가 불가능하다. 아직 돈은 안 움직였으므로 보상은 필요 없다.
            throw new DownstreamCallFailedException(transactionId, request.getAmount(), e);
        }
    }

    private PaymentResponse processDebit(String transactionId, Card card, PaymentRequest request) {
        log.info("체크카드 승인 - 거래ID: {}", transactionId);

        if (card.getPerTransactionLimit() != null && request.getAmount() > card.getPerTransactionLimit()) {
            log.warn("1회 결제 한도 초과 - 거래ID: {}, 요청금액: {}, 한도: {}",
                    transactionId, request.getAmount(), card.getPerTransactionLimit());
            return recordAndRespond(transactionId, card.getCardNumber(), request, "61", "1회 결제 한도 초과", false);
        }

        WithdrawResponse withdrawResponse;
        try {
            withdrawResponse = bankClient.withdraw(
                    new WithdrawRequest(request.getCardNum(), request.getAmount(), transactionId));
        } catch (Exception e) {
            // 출금이 실제로 됐는지 알 수 없다. 취소를 함부로 보내지 않고 대사 대상으로만 남긴다.
            compensationService.recordUncertainWithdrawal(transactionId, request);
            throw new DownstreamCallFailedException(transactionId, request.getAmount(), e);
        }

        if (!withdrawResponse.isSuccess()) {
            log.warn("출금 실패 - 거래ID: {}", transactionId);
            return recordAndRespond(transactionId, card.getCardNumber(), request, "51", "출금 실패", false);
        }

        log.info("체크카드 승인 성공 - 거래ID: {}", transactionId);
        // 출금은 이미 확정됐다. 이후 원장 기록이 실패하면 은행 취소로 되돌려야 한다.
        return recordAndRespond(transactionId, card.getCardNumber(), request, "00", "결제 성공", true, true);
    }

    private PaymentResponse processCredit(String transactionId, Card card, PaymentRequest request) {
        log.info("신용카드 승인 - 거래ID: {}", transactionId);

        if (card.getPerTransactionLimit() != null && request.getAmount() > card.getPerTransactionLimit()) {
            log.warn("1회 결제 한도 초과 - 거래ID: {}, 요청금액: {}, 한도: {}",
                    transactionId, request.getAmount(), card.getPerTransactionLimit());
            return recordAndRespond(transactionId, card.getCardNumber(), request, "61", "1회 결제 한도 초과", false);
        }

        if (card.getCreditLimit() == null || request.getAmount() > card.remainingCredit()) {
            log.warn("신용 한도 초과 - 거래ID: {}, 요청금액: {}, 잔여한도: {}",
                    transactionId, request.getAmount(), card.remainingCredit());
            return recordAndRespond(transactionId, card.getCardNumber(), request, "51", "신용 한도 초과", false);
        }

        // 신용카드는 은행 출금 없이 카드사가 한도를 차감한다(실제 청구는 결제일 배치).
        card.recordCreditUsage(request.getAmount());

        log.info("신용카드 승인 성공 - 거래ID: {}, 누적사용액: {}", transactionId, card.getUsedAmount());
        return recordAndRespond(transactionId, card.getCardNumber(), request, "00", "결제 성공", true);
    }

    private PaymentResponse recordAndRespond(String transactionId, String cardNumber, PaymentRequest request,
                                             String responseCode, String message, boolean success) {
        return recordAndRespond(transactionId, cardNumber, request, responseCode, message, success, false);
    }

    /**
     * 승인 결과를 원장 서비스에 기록하고 응답을 만든다.
     *
     * @param bankWithdrawn 은행 출금이 이미 확정된 상태인지 여부.
     *                      true인데 원장 기록이 실패하면 "돈은 빠졌는데 기록이 없는" 상태라 보상이 필요하다.
     */
    private PaymentResponse recordAndRespond(String transactionId, String cardNumber, PaymentRequest request,
                                             String responseCode, String message, boolean success,
                                             boolean bankWithdrawn) {
        try {
            ledgerClient.record(LedgerRecordRequest.builder()
                    .transactionId(transactionId)
                    .cardNumber(cardNumber)
                    .amount(request.getAmount())
                    .merchantId(request.getMerchantId())
                    .responseCode(responseCode)
                    .success(success)
                    .build());
        } catch (Exception e) {
            log.error("원장 기록 실패 - 거래ID: {}, 은행출금여부: {}", transactionId, bankWithdrawn, e);
            if (bankWithdrawn) {
                compensationService.compensateWithdrawal(transactionId, request, e);
            }
            throw new LedgerRecordFailedException(transactionId, request.getAmount(), e);
        }

        return PaymentResponse.builder()
                .transactionId(transactionId)
                .responseCode(responseCode)
                .message(message)
                .amount(request.getAmount())
                .processedAt(LocalDateTime.now())
                .success(success)
                .build();
    }
}
