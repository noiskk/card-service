package com.card.fds.rule.rules;

import com.card.fds.rule.FdsContext;
import com.card.fds.rule.FdsRule;
import com.card.fds.rule.FdsRuleProperties;
import com.card.fds.rule.RuleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 짧은 시간에 같은 카드로 반복 결제되는 패턴.
 * 카드를 훔친 뒤 짧은 시간에 최대한 많이 쓰려는 전형적인 행동이다.
 *
 * 같은 멱등키 재시도는 이력에 집계되지 않으므로(TransactionHistoryStore) 여기 걸리지 않는다.
 */
@Component
@RequiredArgsConstructor
public class VelocityCountRule implements FdsRule {

    private final FdsRuleProperties props;

    @Override
    public String name() {
        return "VELOCITY_COUNT";
    }

    @Override
    public RuleResult evaluate(FdsContext ctx) {
        // 이번 거래를 포함해 센다 — 임계치는 "이 거래까지 포함해 몇 건인가" 기준이다
        int total = ctx.within(props.getVelocityWindowMinutes()).size() + 1;
        if (total > props.getVelocityMaxCount()) {
            return RuleResult.hit(name(), props.getVelocityScore(),
                    "%d분 내 %d건 결제 (임계 %d건)".formatted(
                            props.getVelocityWindowMinutes(), total, props.getVelocityMaxCount()));
        }
        return RuleResult.pass(name());
    }
}
