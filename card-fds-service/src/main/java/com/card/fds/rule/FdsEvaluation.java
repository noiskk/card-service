package com.card.fds.rule;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 룰 엔진의 최종 산출물 — 점수, 판정, 적발된 룰 목록.
 *
 * 적발 룰과 사유를 함께 남기는 이유: "왜 차단됐는지" 설명할 수 없는 FDS는 운영이 불가능하다.
 * 고객 문의가 오면 근거를 대야 하고, 오탐이면 어느 룰의 임계치를 조정할지 판단해야 한다.
 */
@Getter
@RequiredArgsConstructor
public class FdsEvaluation {

    private final FdsDecision decision;
    private final int riskScore;
    private final List<RuleResult> hits;

    public boolean isBlocked() {
        return decision == FdsDecision.BLOCK;
    }

    /** 적발 룰 이름을 콤마로 이은 문자열 (로그·응답용) */
    public String hitRuleNames() {
        return hits.stream().map(RuleResult::getRuleName).reduce((a, b) -> a + "," + b).orElse("");
    }

    /** 가장 점수가 높은 룰의 사유 — 대표 차단 사유로 쓴다 */
    public String primaryReason() {
        return hits.stream()
                .max((a, b) -> Integer.compare(a.getScore(), b.getScore()))
                .map(RuleResult::getReason)
                .orElse(null);
    }
}
