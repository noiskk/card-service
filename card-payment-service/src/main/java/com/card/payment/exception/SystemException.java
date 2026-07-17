package com.card.payment.exception;


import org.springframework.http.HttpStatus;

/**
 * 결제 처리 중 예상하지 못한 실패(외부 서비스 호출 실패 등)가 발생했음을 나타내는 예외.
 *
 * 이미 외부 호출(은행 등)이 일어난 상태였다면, 그 호출이 실제로 성공했는지 실패했는지
 * 우리 쪽에서는 확신할 수 없다 -> 3단계 Saga에서 보상/재조회로 다룰 대상.
 */
public abstract class SystemException extends DomainException {
    protected SystemException(String message, Throwable cause, String errorCode, HttpStatus httpStatus, String transactionId, Long amount) {
        super(message, cause, errorCode, httpStatus, transactionId, amount);
    }
}
