package com.card.payment.batch;

import com.card.payment.client.BankClient;
import com.card.payment.client.FdsClient;
import com.card.payment.client.LedgerClient;
import com.card.payment.dto.CancelResponse;
import com.card.payment.entity.ReconciliationStatus;
import com.card.payment.entity.UncertainReason;
import com.card.payment.entity.UncertainTransaction;
import com.card.payment.repository.UncertainTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 망취소 대사 배치 통합 테스트.
 *
 * 배치가 실제로 하는 판단 두 가지를 검증한다:
 *  - 은행에 원거래가 없으면(출금 미발생) 취소 없이 종결
 *  - 원거래가 있으면 취소하고 종결
 * 그리고 처리 도중 대상이 PENDING에서 빠져도 남은 건을 건너뛰지 않는지(keyset paging)도 함께 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("망취소 대사 배치 테스트")
class ReconciliationBatchTest {

    @Autowired
    private JobLauncher jobLauncher;
    @Autowired
    private Job reconciliationJob;
    @Autowired
    private UncertainTransactionRepository uncertainRepository;

    @MockBean
    private BankClient bankClient;
    @MockBean
    private FdsClient fdsClient;
    @MockBean
    private LedgerClient ledgerClient;

    @BeforeEach
    void setUp() {
        uncertainRepository.deleteAll();
    }

    private void savePending(String txId, UncertainReason reason) {
        uncertainRepository.save(UncertainTransaction.builder()
                .transactionId(txId)
                .cardNumber("4111111111111111")
                .amount(50_000L)
                .merchantId("M1")
                .reason(reason)
                .status(ReconciliationStatus.PENDING)
                .attemptCount(0)
                .build());
    }

    private void runJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("runAt", System.currentTimeMillis())
                .toJobParameters();
        jobLauncher.run(reconciliationJob, params);
    }

    @Test
    @DisplayName("은행에 원거래가 없으면 출금 미발생으로 종결한다")
    void originalNotFound_resolvedAsNoWithdrawal() throws Exception {
        savePending("TX-1", UncertainReason.BANK_CALL_FAILED);
        when(bankClient.cancel(any())).thenReturn(CancelResponse.builder()
                .success(true).originalFound(false).responseCode("00").build());

        runJob();

        assertThat(uncertainRepository.findByTransactionId("TX-1")).get()
                .satisfies(tx -> {
                    assertThat(tx.getStatus()).isEqualTo(ReconciliationStatus.RESOLVED);
                    assertThat(tx.getResolution()).contains("출금 미발생");
                });
    }

    @Test
    @DisplayName("은행에 원거래가 있으면 취소하고 종결한다")
    void originalFound_cancelled() throws Exception {
        savePending("TX-2", UncertainReason.BANK_CALL_FAILED);
        when(bankClient.cancel(any())).thenReturn(CancelResponse.builder()
                .success(true).originalFound(true).balanceAfter(100_000L).responseCode("00").build());

        runJob();

        assertThat(uncertainRepository.findByTransactionId("TX-2")).get()
                .satisfies(tx -> {
                    assertThat(tx.getStatus()).isEqualTo(ReconciliationStatus.RESOLVED);
                    assertThat(tx.getResolution()).contains("취소");
                });
    }

    @Test
    @DisplayName("은행이 계속 응답하지 않으면 PENDING으로 두고 다음 회차에 다시 시도한다")
    void bankStillDown_staysPending() throws Exception {
        savePending("TX-3", UncertainReason.BANK_CALL_FAILED);
        when(bankClient.cancel(any())).thenThrow(new RuntimeException("은행 여전히 다운"));

        runJob();

        assertThat(uncertainRepository.findByTransactionId("TX-3")).get()
                .satisfies(tx -> {
                    assertThat(tx.getStatus()).isEqualTo(ReconciliationStatus.PENDING);
                    assertThat(tx.getAttemptCount()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("최대 시도 횟수를 넘기면 수동 확인 대상(FAILED)으로 돌린다")
    void exceedsMaxAttempts_markedFailed() throws Exception {
        savePending("TX-4", UncertainReason.BANK_CALL_FAILED);
        when(bankClient.cancel(any())).thenThrow(new RuntimeException("은행 계속 다운"));

        runJob();
        runJob();
        runJob();

        assertThat(uncertainRepository.findByTransactionId("TX-4")).get()
                .satisfies(tx -> {
                    assertThat(tx.getStatus()).isEqualTo(ReconciliationStatus.FAILED);
                    assertThat(tx.getResolution()).contains("수동 확인");
                });
    }

    @Test
    @DisplayName("여러 건이 처리되며 대상 집합이 줄어도 건너뛰지 않는다")
    void allItemsProcessed_noSkipDespiteShrinkingResultSet() throws Exception {
        for (int i = 1; i <= 120; i++) {
            savePending("TX-BULK-" + i, UncertainReason.BANK_CALL_FAILED);
        }
        when(bankClient.cancel(any())).thenReturn(CancelResponse.builder()
                .success(true).originalFound(false).responseCode("00").build());

        runJob();

        // 청크(50)보다 큰 건수를 넣어도 전부 처리돼야 한다
        verify(bankClient, times(120)).cancel(any());
        assertThat(uncertainRepository.findByStatus(ReconciliationStatus.PENDING)).isEmpty();
        assertThat(uncertainRepository.findByStatus(ReconciliationStatus.RESOLVED)).hasSize(120);
    }
}
