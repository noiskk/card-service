package com.card.payment.controller;

import com.card.payment.batch.ReconciliationScheduler;
import com.card.payment.entity.ReconciliationStatus;
import com.card.payment.entity.UncertainTransaction;
import com.card.payment.repository.UncertainTransactionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 망취소 대사 운영용 엔드포인트.
 * 정기 배치와 별개로 즉시 실행하거나, 미해결 건을 확인할 때 쓴다.
 */
@RestController
@RequestMapping("/api/card/reconciliation")
@RequiredArgsConstructor
@Tag(name = "Reconciliation", description = "망취소 대사 운영 API")
public class ReconciliationController {

    private final ReconciliationScheduler reconciliationScheduler;
    private final UncertainTransactionRepository uncertainRepository;

    @Operation(summary = "대사 배치 즉시 실행")
    @PostMapping("/run")
    public String run() {
        reconciliationScheduler.run("manual");
        return "대사 배치를 실행했습니다";
    }

    @Operation(summary = "미해결 불확실 거래 조회")
    @GetMapping("/pending")
    public List<UncertainTransaction> pending() {
        return uncertainRepository.findByStatus(ReconciliationStatus.PENDING);
    }

    @Operation(summary = "수동 확인이 필요한 거래 조회")
    @GetMapping("/failed")
    public List<UncertainTransaction> failed() {
        return uncertainRepository.findByStatus(ReconciliationStatus.FAILED);
    }
}
