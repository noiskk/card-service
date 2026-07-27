package com.card.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 원장 기록 응답 (← ledger-service).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerRecordResponse {
    private Long id;
    private String transactionId;
    private String status;
    private LocalDateTime recordedAt;
}
