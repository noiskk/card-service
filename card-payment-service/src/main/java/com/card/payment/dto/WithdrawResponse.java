package com.card.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 은행 출금 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawResponse {
    private boolean success;
    private String transactionId;
    private String accountNumber;
    private Long amount;
    private Long balanceAfter;
    private LocalDateTime transactionDate;
    private String responseCode;
    private String responseMessage;
}
