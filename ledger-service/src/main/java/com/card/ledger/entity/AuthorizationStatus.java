package com.card.ledger.entity;

public enum AuthorizationStatus {
    APPROVED,   // 승인
    REJECTED    // 거절
    // 취소(CANCELLED)·매출확정(CONFIRMED)은 UPDATE가 아니라 새 원장 레코드로 기록한다(INSERT-only).
}
