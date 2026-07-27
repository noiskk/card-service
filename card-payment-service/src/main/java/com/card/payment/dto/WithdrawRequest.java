package com.card.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 은행 출금 요청 DTO.
 * transactionId는 은행 거래 내역에 참조로 남아, 이후 취소·대사에서 원거래를 찾는 키가 된다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawRequest {
    private String cardNum;
    private Long amount;
    private String transactionId;
}
