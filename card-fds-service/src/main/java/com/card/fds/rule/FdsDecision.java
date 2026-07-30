package com.card.fds.rule;

/**
 * 위험 점수 구간에 따른 최종 판정.
 *
 * 실무 FDS는 통과/차단 이진 판정이 아니다. 애매한 구간은 승인하되 표시를 남겨
 * 추가 인증이나 사후 모니터링으로 넘긴다. 차단은 확실할 때만 한다
 * (정상 고객의 결제를 막는 비용이 사기 손실보다 클 수 있기 때문).
 */
public enum FdsDecision {
    APPROVE,
    REVIEW,
    BLOCK
}
