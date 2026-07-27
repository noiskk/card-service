package com.card.payment.entity;

/**
 * 불확실 거래의 대사(reconciliation) 처리 상태.
 */
public enum ReconciliationStatus {
    /** 아직 정리되지 않음 — 대사 배치의 처리 대상 */
    PENDING,
    /** 대사 완료 (취소 또는 정상 종결) */
    RESOLVED,
    /** 대사 시도했으나 실패 — 수동 확인 필요 */
    FAILED
}
