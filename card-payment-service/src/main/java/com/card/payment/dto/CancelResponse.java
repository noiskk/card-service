package com.card.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 출금 취소(보상) 응답 (← bank-service).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelResponse {
    private boolean success;
    private String transactionId;
    private Long balanceAfter;
    private String responseCode;
    private String responseMessage;
    /** 은행에 해당 출금 기록이 존재했는지 — 대사 배치가 "출금 안 됨"을 판별하는 근거 */
    private boolean originalFound;
}
