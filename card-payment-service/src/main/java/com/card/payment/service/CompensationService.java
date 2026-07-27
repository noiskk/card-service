package com.card.payment.service;

import com.card.payment.client.BankClient;
import com.card.payment.dto.CancelRequest;
import com.card.payment.dto.CancelResponse;
import com.card.payment.dto.PaymentRequest;
import com.card.payment.entity.ReconciliationStatus;
import com.card.payment.entity.UncertainReason;
import com.card.payment.entity.UncertainTransaction;
import com.card.payment.repository.UncertainTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보상 트랜잭션 / 망취소 대상 기록.
 *
 * 모든 메서드가 REQUIRES_NEW인 이유: 호출하는 쪽(PaymentExecutor)은 예외를 던지며 롤백되는 흐름이라,
 * 같은 트랜잭션에 묶이면 여기서 남긴 기록까지 함께 사라진다. 불확실 거래 기록은 반드시 살아남아야 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompensationService {

    private final BankClient bankClient;
    private final UncertainTransactionRepository uncertainRepository;

    /**
     * 출금은 성공했는데 이후 처리(원장 기록)가 실패한 경우의 보상.
     *
     * 순서가 중요하다: 먼저 불확실 거래로 남긴 뒤 취소를 시도한다.
     * 취소 도중 프로세스가 죽어도 기록이 남아 배치가 이어받을 수 있다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensateWithdrawal(String transactionId, PaymentRequest request, Throwable cause) {
        UncertainTransaction uncertain = saveUncertain(
                transactionId, request, UncertainReason.LEDGER_RECORD_FAILED);

        try {
            CancelResponse response = bankClient.cancel(CancelRequest.builder()
                    .cardNum(request.getCardNum())
                    .amount(request.getAmount())
                    .transactionId(transactionId)
                    .build());

            if (response.isSuccess()) {
                uncertain.resolve("원장 기록 실패로 출금 보상 취소 완료");
                log.info("보상 취소 성공 - 거래ID: {}", transactionId);
            } else {
                log.error("보상 취소 거절 - 거래ID: {}, 코드: {}", transactionId, response.getResponseCode());
            }
        } catch (Exception e) {
            // 보상까지 실패하면 동기적으로 할 수 있는 게 없다. PENDING으로 남겨 배치가 재시도한다.
            log.error("보상 취소 호출 실패 - 거래ID: {} (대사 배치가 재처리)", transactionId, e);
        }
    }

    /**
     * 은행 호출 자체가 실패해 출금 성공 여부를 알 수 없는 경우.
     * 여기서는 취소를 시도하지 않는다 — 출금이 안 됐을 수도 있어 함부로 취소하면 안 되기 때문.
     * 실제 은행 상태 확인은 대사 배치가 수행한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUncertainWithdrawal(String transactionId, PaymentRequest request) {
        saveUncertain(transactionId, request, UncertainReason.BANK_CALL_FAILED);
        log.warn("불확실 거래 기록 - 거래ID: {} (은행 응답 불명, 대사 대상)", transactionId);
    }

    private UncertainTransaction saveUncertain(String transactionId, PaymentRequest request,
                                               UncertainReason reason) {
        return uncertainRepository.findByTransactionId(transactionId)
                .orElseGet(() -> uncertainRepository.save(UncertainTransaction.builder()
                        .transactionId(transactionId)
                        .cardNumber(request.getCardNum())
                        .amount(request.getAmount())
                        .merchantId(request.getMerchantId())
                        .reason(reason)
                        .status(ReconciliationStatus.PENDING)
                        .attemptCount(0)
                        .build()));
    }
}
