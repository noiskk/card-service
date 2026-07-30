package com.card.fds.rule.rules;

import com.card.fds.rule.FdsContext;
import com.card.fds.rule.FdsRule;
import com.card.fds.rule.FdsRuleProperties;
import com.card.fds.rule.RuleResult;
import com.card.fds.rule.TransactionRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 짧은 시간에 누적 금액이 과도한 패턴 — 한도를 빠르게 소진하려는 시도.
 * 건수 룰을 피하려고 적은 건수로 큰 금액을 쓰는 경우를 잡는다.
 */
@Component
@RequiredArgsConstructor
public class VelocityAmountRule implements FdsRule {

    private final FdsRuleProperties props;

    @Override
    public String name() {
        return "VELOCITY_AMOUNT";
    }

    @Override
    public RuleResult evaluate(FdsContext ctx) {
        BigDecimal total = ctx.within(props.getAmountWindowMinutes()).stream()
                .map(TransactionRecord::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(ctx.getAmount() != null ? ctx.getAmount() : BigDecimal.ZERO);

        if (total.compareTo(props.getAmountMaxTotal()) > 0) {
            return RuleResult.hit(name(), props.getAmountScore(),
                    "%d분 내 누적 %s원 (임계 %s원)".formatted(
                            props.getAmountWindowMinutes(),
                            total.toPlainString(),
                            props.getAmountMaxTotal().toPlainString()));
        }
        return RuleResult.pass(name());
    }
}
