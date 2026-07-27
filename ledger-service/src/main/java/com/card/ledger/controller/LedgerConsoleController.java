package com.card.ledger.controller;

import com.card.ledger.batch.SettlementJobConfig;
import com.card.ledger.entity.Authorization;
import com.card.ledger.repository.AuthorizationRepository;
import com.card.ledger.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * 원장 관제 화면 (시연용).
 * 승인/거절이 원장에 쌓이는 것과, 정산 배치가 가맹점별로 수수료를 떼는 과정을 보여준다.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class LedgerConsoleController {

    private final AuthorizationRepository authorizationRepository;
    private final SettlementRepository settlementRepository;
    private final JobLauncher jobLauncher;

    @Qualifier(SettlementJobConfig.JOB_NAME)
    private final Job settlementJob;

    @GetMapping("/")
    public String console(@RequestParam(required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
                          Model model) {
        LocalDate date = (targetDate != null) ? targetDate : LocalDate.now();

        List<Authorization> records = authorizationRepository.findAll().stream()
                .sorted(Comparator.comparing(Authorization::getCreatedAt).reversed())
                .limit(30)
                .toList();

        model.addAttribute("records", records);
        model.addAttribute("targetDate", date);
        model.addAttribute("settlements", settlementRepository.findBySettlementDate(date));
        model.addAttribute("approvedCount",
                records.stream().filter(r -> "00".equals(r.getResponseCode())).count());
        return "ledger-console";
    }

    /** 화면에서 정산 배치를 직접 돌려본다. */
    @PostMapping("/console/settle")
    public String settle(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
                         Model model) {
        try {
            jobLauncher.run(settlementJob, new JobParametersBuilder()
                    .addString("targetDate", targetDate.toString())
                    .addLong("runAt", System.currentTimeMillis())
                    .toJobParameters());
        } catch (Exception e) {
            log.error("정산 배치 실행 실패", e);
        }
        return "redirect:/?targetDate=" + targetDate;
    }
}
