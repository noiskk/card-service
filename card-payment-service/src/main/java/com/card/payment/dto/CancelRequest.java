package com.card.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 출금 취소(보상) 요청 (→ bank-service).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelRequest {
    private String cardNum;
    private Long amount;
    /** 원 거래 ID — 은행이 같은 취소를 중복 처리하지 않도록 하는 멱등키 역할 */
    private String transactionId;
}
