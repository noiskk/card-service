package com.card.fds.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * FDS 판정 결과.
 *
 * 차단이든 통과든 HTTP 200으로 응답하고 결과는 responseCode로 구분한다.
 * 점수와 적발 룰을 함께 실어 보내 호출자·운영자가 판정 근거를 알 수 있게 한다
 * — 근거를 설명하지 못하는 FDS는 오탐이 나도 어디를 고칠지 알 수 없다.
 */
@Getter
@Builder
public class FdsResponse {
    private boolean success;
    private String responseCode;
    private String message;

    /** DB에서 확인한 실제 카드 타입(CREDIT/DEBIT). 요청에 실려온 값이 틀릴 수 있어 FDS가 보정해 알려준다. */
    private String cardType;

    /** APPROVE / REVIEW / BLOCK */
    private String decision;

    /** 합산 위험 점수 */
    private int riskScore;

    /** 적발된 룰 이름 (콤마 구분) */
    private String hitRules;
}
