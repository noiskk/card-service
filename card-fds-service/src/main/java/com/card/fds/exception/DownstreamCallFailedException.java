package com.card.fds.exception;

import org.springframework.http.HttpStatus;

/**
 * 하위 결제 서비스(card-payment-service) 호출이 실패했음을 나타내는 예외.
 *
 * 비즈니스 거절(200)과 달리, 이건 진짜 시스템 실패이므로 5xx로 전파한다.
 * -> 이를 호출하는 VAN의 Feign이 예외로 받아 시스템 실패로 처리하게 됨
 * ("비즈니스 결과=200 relay / 시스템 실패=5xx 전파" 체인 일관성).
 */
public class DownstreamCallFailedException extends SystemException {
    public DownstreamCallFailedException(Throwable cause) {
        super("결제 서비스 호출 실패", cause, "96", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
