package com.card.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 원장 기록 요청. 승인 서비스가 승인/거절 결과를 확정한 뒤 이 원장에 기록한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerRecordRequest {

    @NotBlank
    private String transactionId;

    @NotBlank
    private String cardNumber;

    @NotNull
    private Long amount;

    @NotBlank
    private String merchantId;

    @NotBlank
    private String responseCode;

    // 승인이면 true(APPROVED), 거절이면 false(REJECTED)
    private boolean success;
}
