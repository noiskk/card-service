package com.card.fds.exception;

import org.springframework.http.HttpStatus;

/**
 * FDS 검증에서 거래가 거절/차단되었음을 나타내는 예외.
 * (예: 단기 다발성 거래 차단, 무효/비활성 카드)
 * 부작용이 발생하기 전에 판단되므로 보상 트랜잭션이 필요 없다.
 */
public abstract class BusinessException extends DomainException {
    protected BusinessException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }
}
