package com.card.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FDS 판정 요청 (→ card-fds-service).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FdsInspectRequest {
    private String cardNum;
    private Long amount;
    private String merchantId;
    private String cardType;
    private String idempotencyKey;
}
