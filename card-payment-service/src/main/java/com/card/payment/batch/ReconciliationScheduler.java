package com.card.payment.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 망취소 대사 Job 실행기.
 *
 * Spring Batch는 같은 Job 이름 + 같은 파라미터 조합을 한 번만 실행한다(JobInstance 중복 방지).
 * 주기 실행이 필요하므로 실행 시각을 파라미터로 넣어 회차마다 다른 JobInstance가 되게 한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationScheduler {

    private final JobLauncher jobLauncher;

    @Qualifier(ReconciliationJobConfig.JOB_NAME)
    private final Job reconciliationJob;

    /** 5분마다 불확실 거래를 정리한다. */
    @Scheduled(fixedDelayString = "${reconciliation.interval-ms:300000}")
    public void runScheduled() {
        run("scheduled");
    }

    public void run(String trigger) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())
                    .addString("trigger", trigger)
                    .toJobParameters();
            jobLauncher.run(reconciliationJob, params);
        } catch (Exception e) {
            log.error("망취소 대사 배치 실행 실패", e);
        }
    }
}
