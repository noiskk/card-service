package com.card.ledger.exception;

import org.springframework.http.HttpStatus;

/**
 * 요청 자체가 잘못되었거나(검증 실패) 대상 원장이 없는 등 클라이언트 책임 오류.
 */
public abstract class BusinessException extends DomainException {
    protected BusinessException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }
}
