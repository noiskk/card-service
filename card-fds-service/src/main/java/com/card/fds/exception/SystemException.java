package com.card.fds.exception;

import org.springframework.http.HttpStatus;

/**
 * FDS 처리 중 예상하지 못한 실패(하위 결제 서비스 호출 실패 등)를 나타내는 예외.
 * 원본 예외를 cause로 감싸 로그에 근본 원인이 남게 한다.
 */
public abstract class SystemException extends DomainException {
    protected SystemException(String message, Throwable cause, String errorCode, HttpStatus httpStatus) {
        super(message, cause, errorCode, httpStatus);
    }
}
