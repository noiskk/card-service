package com.card.fds.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * FDS 판정 결과.
 *
 * FDS는 사기 여부만 판정하고 승인 흐름을 이어가지 않는다(leaf 서비스).
 * 차단이든 통과든 HTTP 200으로 응답하고 결과는 responseCode로 구분한다.
 */
@Getter
@Builder
public class FdsResponse {
    private boolean success;
    private String responseCode;
    private String message;

    /** DB에서 확인한 실제 카드 타입(CREDIT/DEBIT). 요청에 실려온 값이 틀릴 수 있어 FDS가 보정해 알려준다. */
    private String cardType;
}
