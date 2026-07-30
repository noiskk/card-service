package com.card.fds.rule.rules;

import com.card.fds.rule.FdsContext;
import com.card.fds.rule.FdsRule;
import com.card.fds.rule.FdsRuleProperties;
import com.card.fds.rule.RuleResult;
import com.card.fds.rule.TransactionRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 짧은 시간에 서로 다른 가맹점에서 결제되는 패턴.
 *
 * 정상 고객은 보통 한 가맹점에서 연달아 결제한다(재시도·추가결제).
 * 반면 도난 카드는 짧은 시간에 여러 가맹점을 옮겨 다니는 경향이 있다.
 * 같은 가맹점 반복은 이 룰에 걸리지 않게 해서 정상 사용을 오탐하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class MerchantDiversityRule implements FdsRule {

    private final FdsRuleProperties props;

    @Override
    public String name() {
        return "MERCHANT_DIVERSITY";
    }

    @Override
    public RuleResult evaluate(FdsContext ctx) {
        Set<String> merchants = new HashSet<>();
        ctx.within(props.getDiversityWindowMinutes()).stream()
                .map(TransactionRecord::getMerchantId)
                .filter(m -> m != null)
                .forEach(merchants::add);
        if (ctx.getMerchantId() != null) {
            merchants.add(ctx.getMerchantId());
        }

        if (merchants.size() > props.getDiversityMaxMerchants()) {
            return RuleResult.hit(name(), props.getDiversityScore(),
                    "%d분 내 서로 다른 가맹점 %d곳 (임계 %d곳)".formatted(
                            props.getDiversityWindowMinutes(),
                            merchants.size(),
                            props.getDiversityMaxMerchants()));
        }
        return RuleResult.pass(name());
    }
}
