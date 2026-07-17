package com.card.payment.exception;

import org.springframework.http.HttpStatus;

/**
 * 결제 요청이 비즈니스 규칙에 의해 거절되었음을 나타내는 예외.
 * (예: 카드 없음, 유효하지 않은 카드 타입, 한도 초과)
 *
 * 같은 요청을 다시 보내도 동일하게 거절된다 — 부작용(외부 호출 등)이
 * 발생하기 전에 판단되므로 보상 트랜잭션이 필요 없다.
 */
public abstract class BusinessException extends DomainException{

    protected BusinessException(String message, String errorCode, HttpStatus httpStatus, String transactionId, Long amount) {
        super(message, errorCode, httpStatus, transactionId, amount);
    }
}
