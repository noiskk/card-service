package com.card.payment.exception;

import org.springframework.http.HttpStatus;

/**
 * 카드 타입 값이 "DEBIT"/"CREDIT" 둘 중 하나가 아님을 나타내는 예외.
 *
 * 클라이언트가 잘못된 값을 보낸 경우이지 시스템 오류가 아니므로 HTTP 200으로 응답한다.
 */
public class InvalidCardTypeException extends BusinessException{
    public InvalidCardTypeException(String transactionId, String cardType) {
        super("유효하지 않은 카드 타입입니다." + cardType, "96", HttpStatus.OK, transactionId, null);
    }
}
