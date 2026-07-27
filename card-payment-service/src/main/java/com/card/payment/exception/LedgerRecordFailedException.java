package com.card.payment.exception;

import org.springframework.http.HttpStatus;

/**
 * 원장 서비스에 승인 결과를 기록하지 못했음을 나타내는 예외.
 *
 * 은행 출금이 이미 성공한 뒤 이 예외가 발생하면 "돈은 빠졌는데 기록이 없는" 상태가 되므로,
 * 보상 트랜잭션(은행 취소)의 대상이 된다.
 */
public class LedgerRecordFailedException extends SystemException {
    public LedgerRecordFailedException(String transactionId, Long amount, Throwable cause) {
        super("원장 기록에 실패했습니다", cause, "96", HttpStatus.SERVICE_UNAVAILABLE, transactionId, amount);
    }
}
