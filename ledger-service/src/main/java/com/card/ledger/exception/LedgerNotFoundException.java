package com.card.ledger.exception;

import org.springframework.http.HttpStatus;

/**
 * 조회하려는 거래ID의 원장이 존재하지 않음.
 */
public class LedgerNotFoundException extends BusinessException {
    public LedgerNotFoundException(String transactionId) {
        super("원장을 찾을 수 없습니다: " + transactionId, "LEDGER_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
