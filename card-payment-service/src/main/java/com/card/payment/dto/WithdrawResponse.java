package com.card.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 은행 출금 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawResponse {
    private boolean success;
    private String message;
}
