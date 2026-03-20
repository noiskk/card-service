package com.card.fds.entity;

/**
 * 승인 상태
 */
public enum AuthorizationStatus {
    /**
     * 승인
     */
    APPROVED,
    
    /**
     * 거절
     */
    REJECTED,
    
    /**
     * 매출 확정
     */
    CONFIRMED
}
