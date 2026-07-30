package com.card.fds.rule.rules;

import com.card.fds.rule.FdsContext;
import com.card.fds.rule.FdsRule;
import com.card.fds.rule.FdsRuleProperties;
import com.card.fds.rule.RuleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 심야 시간대 고액 결제.
 *
 * 이것만으로는 사기라고 볼 수 없어서(정상 심야 결제도 많다) 점수를 낮게 준다.
 * 다른 룰과 함께 걸릴 때 합산 점수를 차단 구간으로 밀어 올리는 역할이다
 * — 점수제를 쓰는 이유가 이런 약한 신호를 버리지 않고 활용하는 것이다.
 */
@Component
@RequiredArgsConstructor
public class MidnightHighAmountRule implements FdsRule {

    private final FdsRuleProperties props;

    @Override
    public String name() {
        return "MIDNIGHT_HIGH_AMOUNT";
    }

    @Override
    public RuleResult evaluate(FdsContext ctx) {
        if (ctx.getAmount() == null) {
            return RuleResult.pass(name());
        }

        int hour = ctx.getNow().getHour();
        boolean midnight = hour >= props.getMidnightFromHour() && hour < props.getMidnightToHour();

        if (midnight && ctx.getAmount().compareTo(props.getMidnightAmountOver()) > 0) {
            return RuleResult.hit(name(), props.getMidnightScore(),
                    "%02d시 %s원 결제 (심야 %d~%d시 고액)".formatted(
                            hour, ctx.getAmount().toPlainString(),
                            props.getMidnightFromHour(), props.getMidnightToHour()));
        }
        return RuleResult.pass(name());
    }
}
