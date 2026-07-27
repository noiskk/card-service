package com.card.ledger.controller;

import com.card.ledger.dto.LedgerRecordRequest;
import com.card.ledger.dto.LedgerRecordResponse;
import com.card.ledger.service.LedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    /** 승인/거절 결과를 원장에 기록한다. */
    @PostMapping("/records")
    public LedgerRecordResponse record(@RequestBody @Valid LedgerRecordRequest request) {
        return ledgerService.record(request);
    }

    /** 거래ID로 원장 조회 (대사·확인용). */
    @GetMapping("/records/{transactionId}")
    public LedgerRecordResponse find(@PathVariable String transactionId) {
        return ledgerService.findByTransactionId(transactionId);
    }
}
