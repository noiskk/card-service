package com.card.payment.controller;

import com.card.payment.batch.ReconciliationScheduler;
import com.card.payment.entity.IdempotencyRecord;
import com.card.payment.entity.ReconciliationStatus;
import com.card.payment.entity.UncertainTransaction;
import com.card.payment.repository.CardInfoRepository;
import com.card.payment.repository.IdempotencyRecordRepository;
import com.card.payment.repository.UncertainTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Comparator;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class PaymentConsoleController {

    private final UncertainTransactionRepository uncertainRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final CardInfoRepository cardInfoRepository;
    private final ReconciliationScheduler reconciliationScheduler;

    @GetMapping("/")
    public String console(Model model) {
        List<UncertainTransaction> uncertain = uncertainRepository.findAll().stream()
                .sorted(Comparator.comparing(UncertainTransaction::getCreatedAt).reversed())
                .toList();

        List<IdempotencyRecord> keys = idempotencyRepository.findAll().stream()
                .sorted(Comparator.comparing(IdempotencyRecord::getCreatedAt).reversed())
                .limit(50)
                .toList();

        long pending = uncertain.stream().filter(u -> u.getStatus() == ReconciliationStatus.PENDING).count();
        long failed = uncertain.stream().filter(u -> u.getStatus() == ReconciliationStatus.FAILED).count();
        long resolved = uncertain.stream().filter(u -> u.getStatus() == ReconciliationStatus.RESOLVED).count();

        model.addAttribute("uncertain", uncertain);
        model.addAttribute("pendingCount", pending);
        model.addAttribute("failedCount", failed);
        model.addAttribute("resolvedCount", resolved);
        model.addAttribute("keys", keys);
        model.addAttribute("keyCount", idempotencyRepository.count());
        model.addAttribute("cards", cardInfoRepository.findAll());
        // 대사 대기나 수동 확인 건이 있으면 상단 상태를 DEGRADED로 표시한다
        model.addAttribute("degraded", pending > 0 || failed > 0);
        return "payment-console";
    }

    @PostMapping("/console/reconcile")
    public String reconcile() {
        reconciliationScheduler.run("console");
        return "redirect:/";
    }
}
