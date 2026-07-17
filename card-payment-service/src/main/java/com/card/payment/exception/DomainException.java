package com.card.payment.exception;

import org.springframework.http.HttpStatus;

/**
 * card-payment-service의 모든 도메인 예외의 최상위 클래스.
 * errorCode/httpStatus를 들고 있어서 GlobalExceptionHandler가 예외 타입만 보고
 * HTTP 응답과 PaymentResponse 바디를 조립할 수 있다.
 * transactionId/amount는 PaymentResponse에 그대로 채워 넣기 위한 컨텍스트다.
 */
public abstract class DomainException extends RuntimeException{

    private final String errorCode;
    private final HttpStatus httpStatus;
    private final String transactionId;
    private final Long amount;

    protected DomainException(String message, String errorCode, HttpStatus httpStatus, String transactionId, Long amount) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.transactionId = transactionId;
        this.amount = amount;
    }

    protected DomainException(String message, Throwable cause, String errorCode, HttpStatus httpStatus, String transactionId, Long amount) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.transactionId = transactionId;
        this.amount = amount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Long getAmount() {
        return amount;
    }
}
