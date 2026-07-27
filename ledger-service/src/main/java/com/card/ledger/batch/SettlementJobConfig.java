package com.card.ledger.batch;

import com.card.ledger.entity.AuthorizationStatus;
import com.card.ledger.entity.Settlement;
import com.card.ledger.repository.AuthorizationRepository;
import com.card.ledger.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.List;

/**
 * 가맹점별 일 정산 Job.
 *
 * 승인 원장을 가맹점 단위로 집계해 수수료를 떼고 지급액을 산출한다.
 * 정산은 돈이 오가는 작업이라 "정확히 한 번"이 중요하므로,
 * (가맹점, 정산일) 조합에 UNIQUE 제약을 두고 처리 단계에서도 이미 정산된 건을 걸러낸다.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class SettlementJobConfig {

    public static final String JOB_NAME = "settlementJob";
    private static final int CHUNK_SIZE = 20;

    private final AuthorizationRepository authorizationRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementProcessor settlementProcessor;

    @Bean
    public Job settlementJob(JobRepository jobRepository, Step settlementStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(settlementStep)
                .build();
    }

    @Bean
    public Step settlementStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("settlementStep", jobRepository)
                .<String, Settlement>chunk(CHUNK_SIZE, transactionManager)
                .reader(merchantIdReader(null))
                .processor(settlementProcessor)
                .writer(settlementWriter())
                .build();
    }

    /**
     * 정산 대상 가맹점 목록을 읽는다.
     * 가맹점 수는 거래 건수에 비해 훨씬 적어 한 번에 조회해도 된다
     * (거래 건 단위로 훑을 필요가 없어 조회 횟수를 줄일 수 있다).
     */
    @Bean
    @StepScope
    public ItemReader<String> merchantIdReader(@Value("#{jobParameters['targetDate']}") String targetDate) {
        LocalDate date = LocalDate.parse(targetDate);
        List<String> merchantIds = authorizationRepository.findMerchantIdsWithApprovals(
                AuthorizationStatus.APPROVED, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        log.info("정산 대상 가맹점 {}곳 - 정산일: {}", merchantIds.size(), date);
        return new ListItemReader<>(merchantIds);
    }

    @Bean
    public ItemWriter<Settlement> settlementWriter() {
        return items -> {
            settlementRepository.saveAll(items);
            log.info("정산 레코드 {}건 저장", items.size());
        };
    }
}
