package com.card.fds.rule;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 룰 하나의 평가 결과.
 * 적발되지 않으면 점수 0이고, 적발되면 룰이 정한 점수와 사유를 함께 반환한다.
 */
@Getter
@RequiredArgsConstructor
public class RuleResult {

    private final String ruleName;
    private final boolean hit;
    private final int score;
    private final String reason;

    public static RuleResult pass(String ruleName) {
        return new RuleResult(ruleName, false, 0, null);
    }

    public static RuleResult hit(String ruleName, int score, String reason) {
        return new RuleResult(ruleName, true, score, reason);
    }
}
