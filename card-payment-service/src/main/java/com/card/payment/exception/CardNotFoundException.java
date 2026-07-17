package com.card.payment.exception;

import org.springframework.http.HttpStatus;

public class CardNotFoundException extends BusinessException{
    public CardNotFoundException(String transactionId, String cardNumber) {
        super("카드 정보를 찾을 수 없습니다: " + cardNumber, "96", HttpStatus.OK, transactionId, null);
    }
}
