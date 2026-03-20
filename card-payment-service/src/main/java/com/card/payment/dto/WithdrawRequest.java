package com.card.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 은행 출금 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawRequest {
    private String cardNum;
    private Long amount;
}
