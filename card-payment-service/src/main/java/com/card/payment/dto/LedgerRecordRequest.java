package com.card.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 원장 기록 요청 (→ ledger-service).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerRecordRequest {
    private String transactionId;
    private String cardNumber;
    private Long amount;
    private String merchantId;
    private String responseCode;
    private boolean success;
}
