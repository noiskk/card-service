package com.card.ledger.controller;

import com.card.ledger.batch.SettlementJobConfig;
import com.card.ledger.entity.Settlement;
import com.card.ledger.repository.SettlementRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 정산 배치 실행·조회 엔드포인트.
 */
@RestController
@RequestMapping("/ledger/settlements")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Settlement", description = "가맹점 정산 API")
public class SettlementController {

    private final JobLauncher jobLauncher;

    @Qualifier(SettlementJobConfig.JOB_NAME)
    private final Job settlementJob;

    private final SettlementRepository settlementRepository;

    @Operation(summary = "정산 배치 실행", description = "지정한 영업일의 가맹점별 정산을 수행합니다. 두 번 실행해도 중복 정산되지 않습니다.")
    @PostMapping("/run")
    public String run(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate)
            throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .addLong("runAt", System.currentTimeMillis())
                .toJobParameters();
        jobLauncher.run(settlementJob, params);
        return targetDate + " 정산 배치를 실행했습니다";
    }

    @Operation(summary = "정산 결과 조회")
    @GetMapping
    public List<Settlement> byDate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {
        return settlementRepository.findBySettlementDate(targetDate);
    }
}
