package com.card.payment.batch;

import com.card.payment.entity.UncertainTransaction;
import com.card.payment.repository.UncertainTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 망취소 대사 Job.
 *
 * 승인 시점에 결과를 확신할 수 없어 남겨둔 거래를 은행에 다시 확인해 정리한다.
 * 청크 단위로 커밋하므로 중간에 실패해도 이미 정리한 건은 유지된다.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ReconciliationJobConfig {

    public static final String JOB_NAME = "reconciliationJob";
    private static final int CHUNK_SIZE = 50;

    private final UncertainTransactionRepository uncertainRepository;
    private final ReconciliationProcessor reconciliationProcessor;

    @Bean
    public Job reconciliationJob(JobRepository jobRepository, Step reconciliationStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(reconciliationStep)
                .build();
    }

    @Bean
    public Step reconciliationStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("reconciliationStep", jobRepository)
                .<UncertainTransaction, UncertainTransaction>chunk(CHUNK_SIZE, transactionManager)
                .reader(pendingUncertainReader())
                .processor(reconciliationProcessor)
                .writer(uncertainWriter())
                .faultTolerant()
                // 한 건이 터져도 배치 전체를 멈추지 않는다. 그 건은 다음 회차가 다시 집는다.
                .skip(Exception.class)
                .skipLimit(100)
                .build();
    }

    /**
     * Reader는 마지막으로 읽은 id를 상태로 들고 있다.
     * 싱글턴이면 그 커서가 다음 실행까지 남아 두 번째 회차부터 아무것도 읽지 못하므로
     * 반드시 실행마다 새로 만들어야 한다.
     */
    @Bean
    @StepScope
    public ItemReader<UncertainTransaction> pendingUncertainReader() {
        return new PendingUncertainReader(uncertainRepository, CHUNK_SIZE);
    }

    @Bean
    public ItemWriter<UncertainTransaction> uncertainWriter() {
        return items -> {
            uncertainRepository.saveAll(items);
            log.info("대사 결과 {}건 저장", items.size());
        };
    }
}
