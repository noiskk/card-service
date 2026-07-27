package com.card.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FDS 판정 결과 (← card-fds-service).
 * 차단도 HTTP 200으로 오므로 success/responseCode로 판별한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FdsInspectResponse {
    private boolean success;
    private String responseCode;
    private String message;
    /** FDS가 DB에서 확인한 실제 카드 타입 */
    private String cardType;
}
