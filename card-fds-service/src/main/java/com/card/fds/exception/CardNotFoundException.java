package com.card.fds.exception;

import org.springframework.http.HttpStatus;

/**
 * 카드번호로 카드를 찾지 못했음을 나타내는 예외.
 * 정상적인 비즈니스 거절이므로 HTTP 200. 응답 코드 14 = 무효 카드.
 */
public class CardNotFoundException extends BusinessException {
    public CardNotFoundException(String cardNum) {
        super("카드를 찾을 수 없습니다: " + cardNum, "14", HttpStatus.OK);
    }
}
