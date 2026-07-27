package com.card.ledger.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class LedgerRecordResponse {
    private Long id;
    private String transactionId;
    private String status;
    private LocalDateTime recordedAt;
}
