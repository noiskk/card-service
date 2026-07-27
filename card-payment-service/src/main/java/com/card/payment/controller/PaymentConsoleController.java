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

/**
 * 승인 서비스 관제 화면 (시연용).
 * 멱등키 처리 현황과, 결과가 불확실해 대사 대상으로 남은 거래를 보여준다.
 */
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
                .limit(20)
                .toList();

        model.addAttribute("uncertain", uncertain);
        model.addAttribute("pendingCount",
                uncertain.stream().filter(u -> u.getStatus() == ReconciliationStatus.PENDING).count());
        model.addAttribute("failedCount",
                uncertain.stream().filter(u -> u.getStatus() == ReconciliationStatus.FAILED).count());
        model.addAttribute("keys", keys);
        model.addAttribute("cards", cardInfoRepository.findAll());
        return "payment-console";
    }

    /** 화면에서 망취소 대사 배치를 즉시 실행한다. */
    @PostMapping("/console/reconcile")
    public String reconcile() {
        reconciliationScheduler.run("console");
        return "redirect:/";
    }
}
