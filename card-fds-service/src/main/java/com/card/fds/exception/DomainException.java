package com.card.fds.exception;

import org.springframework.http.HttpStatus;

/**
 * card-fds-service의 모든 도메인 예외의 최상위 클래스.
 * errorCode/httpStatus를 들고 있어서 GlobalExceptionHandler가 예외 타입만 보고
 * FdsResponse 바디와 HTTP 상태를 조립할 수 있다.
 */
public abstract class DomainException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    protected DomainException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected DomainException(String message, Throwable cause, String errorCode, HttpStatus httpStatus) {
        super(message, cause);
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
