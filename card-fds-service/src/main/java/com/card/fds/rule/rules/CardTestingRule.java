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
 * 카드 유효성 확인 공격(card testing).
 *
 * 유출된 카드번호가 살아있는지 확인하려고 소액으로 여러 번 긁어본다.
 * 금액이 작아 금액 기반 룰에는 걸리지 않으므로 "소액 + 반복"을 별도 룰로 잡는다.
 */
@Component
@RequiredArgsConstructor
public class CardTestingRule implements FdsRule {

    private final FdsRuleProperties props;

    @Override
    public String name() {
        return "CARD_TESTING";
    }

    @Override
    public RuleResult evaluate(FdsContext ctx) {
        if (!isSmall(ctx.getAmount())) {
            return RuleResult.pass(name());
        }

        // 이번 거래를 포함해 센다
        long total = ctx.within(props.getTestingWindowMinutes()).stream()
                .map(TransactionRecord::getAmount)
                .filter(this::isSmall)
                .count() + 1;

        if (total > props.getTestingMaxCount()) {
            return RuleResult.hit(name(), props.getTestingScore(),
                    "%d분 내 소액(%s원 미만) %d건".formatted(
                            props.getTestingWindowMinutes(),
                            props.getTestingAmountUnder().toPlainString(),
                            total));
        }
        return RuleResult.pass(name());
    }

    private boolean isSmall(BigDecimal amount) {
        return amount != null && amount.compareTo(props.getTestingAmountUnder()) < 0;
    }
}
