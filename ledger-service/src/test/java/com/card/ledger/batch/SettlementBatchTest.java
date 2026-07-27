package com.card.ledger.batch;

import com.card.ledger.entity.Authorization;
import com.card.ledger.entity.AuthorizationStatus;
import com.card.ledger.repository.AuthorizationRepository;
import com.card.ledger.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가맹점 정산 배치 통합 테스트.
 *
 * 정산은 돈이 오가는 작업이라 두 가지가 핵심이다:
 *  - 금액 계산(수수료 차감)이 정확한가
 *  - 배치를 여러 번 돌려도 중복 정산되지 않는가
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("가맹점 정산 배치 테스트")
class SettlementBatchTest {

    private static final LocalDate TARGET = LocalDate.of(2026, 7, 20);

    @Autowired
    private JobLauncher jobLauncher;
    @Autowired
    private Job settlementJob;
    @Autowired
    private AuthorizationRepository authorizationRepository;
    @Autowired
    private SettlementRepository settlementRepository;

    @BeforeEach
    void setUp() {
        settlementRepository.deleteAll();
        authorizationRepository.deleteAll();
    }

    private void saveAuthorization(String merchantId, long amount, AuthorizationStatus status) {
        authorizationRepository.save(Authorization.builder()
                .transactionId("TX-" + merchantId + "-" + amount + "-" + System.nanoTime())
                .cardNumber("4111111111111111")
                .amount(amount)
                .merchantId(merchantId)
                .responseCode(status == AuthorizationStatus.APPROVED ? "00" : "51")
                .status(status)
                .createdAt(TARGET.atTime(12, 0))
                .build());
    }

    private void runJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("targetDate", TARGET.toString())
                .addLong("runAt", System.currentTimeMillis())
                .toJobParameters();
        jobLauncher.run(settlementJob, params);
    }

    @Test
    @DisplayName("가맹점별로 승인분을 모아 수수료를 떼고 지급액을 만든다")
    void aggregatesAndDeductsFee() throws Exception {
        saveAuthorization("MERCHANT-A", 100_000L, AuthorizationStatus.APPROVED);
        saveAuthorization("MERCHANT-A", 50_000L, AuthorizationStatus.APPROVED);
        saveAuthorization("MERCHANT-B", 200_000L, AuthorizationStatus.APPROVED);

        runJob();

        assertThat(settlementRepository.findByMerchantIdAndSettlementDate("MERCHANT-A", TARGET)).get()
                .satisfies(s -> {
                    assertThat(s.getTransactionCount()).isEqualTo(2);
                    assertThat(s.getTotalAmount()).isEqualTo(150_000L);
                    // 150,000 * 2.3% = 3,450
                    assertThat(s.getFeeAmount()).isEqualTo(3_450L);
                    assertThat(s.getPayoutAmount()).isEqualTo(146_550L);
                    // 그때 적용한 요율이 결과와 함께 남는다
                    assertThat(s.getFeeRate()).isEqualByComparingTo(new BigDecimal("0.023"));
                });

        assertThat(settlementRepository.findBySettlementDate(TARGET)).hasSize(2);
    }

    @Test
    @DisplayName("거절된 승인은 정산 대상에서 제외한다")
    void excludesRejected() throws Exception {
        saveAuthorization("MERCHANT-A", 100_000L, AuthorizationStatus.APPROVED);
        saveAuthorization("MERCHANT-A", 999_000L, AuthorizationStatus.REJECTED);

        runJob();

        assertThat(settlementRepository.findByMerchantIdAndSettlementDate("MERCHANT-A", TARGET)).get()
                .satisfies(s -> {
                    assertThat(s.getTransactionCount()).isEqualTo(1);
                    assertThat(s.getTotalAmount()).isEqualTo(100_000L);
                });
    }

    @Test
    @DisplayName("가맹점별 개별 수수료율이 있으면 그 값을 적용한다")
    void appliesMerchantSpecificRate() throws Exception {
        saveAuthorization("MERCHANT-VIP", 1_000_000L, AuthorizationStatus.APPROVED);

        runJob();

        assertThat(settlementRepository.findByMerchantIdAndSettlementDate("MERCHANT-VIP", TARGET)).get()
                .satisfies(s -> {
                    assertThat(s.getFeeRate()).isEqualByComparingTo(new BigDecimal("0.015"));
                    assertThat(s.getFeeAmount()).isEqualTo(15_000L);
                    assertThat(s.getPayoutAmount()).isEqualTo(985_000L);
                });
    }

    @Test
    @DisplayName("같은 날짜로 배치를 두 번 돌려도 중복 정산되지 않는다")
    void runningTwiceDoesNotDoubleSettle() throws Exception {
        saveAuthorization("MERCHANT-A", 100_000L, AuthorizationStatus.APPROVED);

        runJob();
        runJob();

        assertThat(settlementRepository.findBySettlementDate(TARGET)).hasSize(1);
        assertThat(settlementRepository.findByMerchantIdAndSettlementDate("MERCHANT-A", TARGET)).get()
                .satisfies(s -> assertThat(s.getTotalAmount()).isEqualTo(100_000L));
    }

    @Test
    @DisplayName("대상일에 승인이 없는 가맹점은 정산 레코드를 만들지 않는다")
    void noApprovalsNoSettlement() throws Exception {
        authorizationRepository.save(Authorization.builder()
                .transactionId("TX-OTHER-DAY")
                .cardNumber("4111111111111111")
                .amount(100_000L)
                .merchantId("MERCHANT-C")
                .responseCode("00")
                .status(AuthorizationStatus.APPROVED)
                .createdAt(TARGET.minusDays(1).atTime(12, 0))
                .build());

        runJob();

        assertThat(settlementRepository.findBySettlementDate(TARGET)).isEmpty();
    }
}
