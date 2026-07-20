package com.card.fds.exception;

import org.springframework.http.HttpStatus;

/**
 * 카드 상태가 ACTIVE가 아님을 나타내는 예외 (정지/분실/해지 등).
 * 정상적인 비즈니스 거절이므로 HTTP 200. 응답 코드 14 = 사용 불가 카드.
 */
public class InactiveCardException extends BusinessException {
    public InactiveCardException(String status) {
        super("사용 불가능한 카드입니다: " + status, "14", HttpStatus.OK);
    }
}
