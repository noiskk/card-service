package com.card.payment.entity;

public enum IdempotencyStatus {
    PENDING,    // 예약됨 - 처리 중
    COMPLETED   // 처리 완료 - 결과가 저장되어 있음
}
