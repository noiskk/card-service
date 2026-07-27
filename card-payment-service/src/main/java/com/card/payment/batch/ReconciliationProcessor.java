package com.card.payment.batch;

import com.card.payment.client.BankClient;
import com.card.payment.dto.CancelRequest;
import com.card.payment.dto.CancelResponse;
import com.card.payment.entity.UncertainTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * 망취소 대사(reconciliation) 처리.
 *
 * 불확실 거래를 은행에 다시 물어봐서 실제 상태를 확인하고 정리한다.
 * 은행의 취소 API는 원거래가 없으면 originalFound=false로 알려주므로,
 * "출금이 아예 안 된 건"과 "출금돼서 취소한 건"을 같은 호출로 구분할 수 있다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationProcessor implements ItemProcessor<UncertainTransaction, UncertainTransaction> {

    /** 이 횟수까지 실패하면 자동 대사를 포기하고 수동 확인 대상으로 돌린다 */
    private static final int MAX_ATTEMPTS = 3;

    private final BankClient bankClient;

    @Override
    public UncertainTransaction process(UncertainTransaction item) {
        try {
            CancelResponse response = bankClient.cancel(CancelRequest.builder()
                    .cardNum(item.getCardNumber())
                    .amount(item.getAmount())
                    .transactionId(item.getTransactionId())
                    .build());

            if (!response.isOriginalFound()) {
                // 은행에 출금 기록 자체가 없다 = 애초에 돈이 안 빠졌다. 되돌릴 것도 없다.
                item.resolve("은행에 출금 기록 없음 - 출금 미발생으로 종결");
                log.info("대사 완료(출금 미발생) - 거래ID: {}", item.getTransactionId());
            } else if (response.isSuccess()) {
                item.resolve("출금 확인됨 - 취소 처리 완료");
                log.info("대사 완료(취소) - 거래ID: {}", item.getTransactionId());
            } else {
                markRetryOrFail(item, "은행이 취소를 거절함: " + response.getResponseMessage());
            }
        } catch (Exception e) {
            // 은행이 여전히 응답하지 않는다. 다음 배치에서 다시 시도한다.
            markRetryOrFail(item, "은행 취소 호출 실패: " + e.getMessage());
            log.warn("대사 실패 - 거래ID: {}, 시도 {}회", item.getTransactionId(), item.getAttemptCount(), e);
        }
        return item;
    }

    private void markRetryOrFail(UncertainTransaction item, String reason) {
        if (item.getAttemptCount() + 1 >= MAX_ATTEMPTS) {
            item.fail(reason + " (최대 시도 초과 - 수동 확인 필요)");
        } else {
            item.retryLater(reason);
        }
    }
}
