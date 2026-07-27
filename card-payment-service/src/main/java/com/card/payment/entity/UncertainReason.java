package com.card.payment.entity;

/**
 * 거래가 불확실 상태로 남은 사유.
 */
public enum UncertainReason {
    /** 은행 출금 호출이 타임아웃/오류 — 실제 출금 여부를 알 수 없음 */
    BANK_CALL_FAILED,
    /** 출금은 성공했으나 원장 기록에 실패 — 보상(취소) 필요 */
    LEDGER_RECORD_FAILED,
    /** 보상(은행 취소) 호출까지 실패 — 배치가 재시도해야 함 */
    COMPENSATION_FAILED
}
