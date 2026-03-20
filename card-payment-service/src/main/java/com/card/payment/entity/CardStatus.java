package com.card.payment.entity;

/**
 * 카드 상태
 */
public enum CardStatus {
    /**
     * 정상
     */
    ACTIVE,
    
    /**
     * 정지
     */
    SUSPENDED,
    
    /**
     * 분실
     */
    LOST,
    
    /**
     * 해지
     */
    TERMINATED
}
