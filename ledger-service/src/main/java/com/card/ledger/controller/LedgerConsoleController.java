package com.card.ledger.controller;

import com.card.ledger.batch.SettlementJobConfig;
import com.card.ledger.entity.Authorization;
import com.card.ledger.entity.AuthorizationStatus;
import com.card.ledger.entity.Settlement;
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

        List<Authorization> all = authorizationRepository.findAll();
        List<Authorization> records = all.stream()
                .sorted(Comparator.comparing(Authorization::getCreatedAt).reversed())
                .limit(50)
                .toList();

        long approved = all.stream().filter(a -> a.getStatus() == AuthorizationStatus.APPROVED).count();
        long approvedAmount = all.stream()
                .filter(a -> a.getStatus() == AuthorizationStatus.APPROVED)
                .mapToLong(Authorization::getAmount).sum();

        List<Settlement> settlements = settlementRepository.findBySettlementDate(date);

        model.addAttribute("records", records);
        model.addAttribute("total", all.size());
        model.addAttribute("approved", approved);
        model.addAttribute("rejected", all.size() - approved);
        model.addAttribute("approvedAmount", approvedAmount);
        model.addAttribute("approvalRate", all.isEmpty() ? "—"
                : String.format("%.1f%%", approved * 100.0 / all.size()));
        model.addAttribute("targetDate", date);
        model.addAttribute("settlements", settlements);
        model.addAttribute("payoutTotal", settlements.stream().mapToLong(Settlement::getPayoutAmount).sum());
        return "ledger-console";
    }

    @PostMapping("/console/settle")
    public String settle(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {
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
