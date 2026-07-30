package com.card.fds.rule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 룰을 모두 평가해 점수를 합산하고 판정을 내린다.
 *
 * 룰 목록을 스프링이 주입해주므로 룰을 추가할 때 이 클래스를 고칠 필요가 없다.
 * 첫 적발에서 멈추지 않고 전부 평가하는 이유: 약한 신호도 합산하면 차단 구간이 될 수 있고,
 * 어떤 룰들이 함께 걸렸는지가 운영에서 중요한 정보이기 때문이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FdsRuleEngine {

    private final List<FdsRule> rules;
    private final FdsRuleProperties props;

    public FdsEvaluation evaluate(FdsContext context) {
        List<RuleResult> hits = new ArrayList<>();
        int score = 0;

        for (FdsRule rule : rules) {
            RuleResult result = rule.evaluate(context);
            if (result.isHit()) {
                hits.add(result);
                score += result.getScore();
                log.debug("룰 적발 - {} (+{}): {}", result.getRuleName(), result.getScore(), result.getReason());
            }
        }

        FdsDecision decision = decide(score);
        return new FdsEvaluation(decision, score, List.copyOf(hits));
    }

    private FdsDecision decide(int score) {
        if (score >= props.getBlockThreshold()) {
            return FdsDecision.BLOCK;
        }
        if (score >= props.getReviewThreshold()) {
            return FdsDecision.REVIEW;
        }
        return FdsDecision.APPROVE;
    }
}
