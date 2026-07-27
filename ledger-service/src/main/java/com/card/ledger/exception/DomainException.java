package com.card.ledger.exception;

import org.springframework.http.HttpStatus;

/**
 * ledger-service의 모든 도메인 예외의 최상위 클래스.
 */
public abstract class DomainException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    protected DomainException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
