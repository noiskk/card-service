package com.card.fds.rule.rules;

import com.card.fds.entity.CardProfile;
import com.card.fds.rule.FdsContext;
import com.card.fds.rule.FdsRule;
import com.card.fds.rule.FdsRuleProperties;
import com.card.fds.rule.RuleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 평소 사용 패턴 대비 과도하게 큰 금액.
 *
 * 절대 금액으로 임계치를 두면 고액 사용자는 늘 걸리고 소액 사용자는 안 걸린다.
 * 그래서 그 카드의 **장기 프로파일**(배치가 집계한 평균)을 기준으로 상대 비교한다.
 *
 * 단기 이력(최근 몇 분)을 기준으로 삼으면 안 된다. 그건 "평소"가 아니라 "방금"이고,
 * 표본이 적어 평균이 요동친다. 프로파일과 velocity는 목적이 다른 데이터다.
 *
 * 프로파일이 없거나(신규 카드) 집계 표본이 부족하면 판정을 보류한다 — 근거 없는 차단을 만들지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AmountAnomalyRule implements FdsRule {

    private final FdsRuleProperties props;

    @Override
    public String name() {
        return "AMOUNT_ANOMALY";
    }

    @Override
    public RuleResult evaluate(FdsContext ctx) {
        CardProfile profile = ctx.getProfile();
        if (profile == null || ctx.getAmount() == null) {
            return RuleResult.pass(name());
        }
        if (profile.getTransactionCount() < props.getAnomalyMinHistory()) {
            return RuleResult.pass(name());   // 표본이 적으면 평균을 신뢰할 수 없다
        }

        BigDecimal avg = profile.getAvgAmount();
        if (avg == null || avg.signum() == 0) {
            return RuleResult.pass(name());
        }

        BigDecimal threshold = avg.multiply(props.getAnomalyMultiplier());
        if (ctx.getAmount().compareTo(threshold) > 0) {
            return RuleResult.hit(name(), props.getAnomalyScore(),
                    "평소 평균 %s원의 %s배 초과 (요청 %s원, 최근 %d일 %d건 기준)".formatted(
                            avg.stripTrailingZeros().toPlainString(),
                            props.getAnomalyMultiplier().toPlainString(),
                            ctx.getAmount().stripTrailingZeros().toPlainString(),
                            profile.getPeriodDays(),
                            profile.getTransactionCount()));
        }
        return RuleResult.pass(name());
    }
}
