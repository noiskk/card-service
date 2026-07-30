package com.card.fds.rule;

import com.card.fds.entity.CardProfile;
import com.card.fds.rule.rules.AmountAnomalyRule;
import com.card.fds.rule.rules.CardTestingRule;
import com.card.fds.rule.rules.MerchantDiversityRule;
import com.card.fds.rule.rules.MidnightHighAmountRule;
import com.card.fds.rule.rules.VelocityAmountRule;
import com.card.fds.rule.rules.VelocityCountRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 룰 엔진 판정 테스트.
 *
 * 점수제를 쓰는 이유가 "단일 룰로는 애매하지만 겹치면 차단"이므로, 그 합산 동작을 중심으로 검증한다.
 */
@DisplayName("FDS 룰 엔진 테스트")
class FdsRuleEngineTest {

    private static final String CARD = "4111111111111111";
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 7, 30, 12, 0);
    private static final LocalDateTime MIDNIGHT = LocalDateTime.of(2026, 7, 30, 2, 0);

    private FdsRuleProperties props;
    private FdsRuleEngine engine;

    @BeforeEach
    void setUp() {
        props = new FdsRuleProperties();
        engine = new FdsRuleEngine(List.of(
                new VelocityCountRule(props),
                new CardTestingRule(props),
                new VelocityAmountRule(props),
                new MerchantDiversityRule(props),
                new AmountAnomalyRule(props),
                new MidnightHighAmountRule(props)
        ), props);
    }

    private TransactionRecord tx(LocalDateTime at, long amount, String merchant) {
        return new TransactionRecord(at, CARD, BigDecimal.valueOf(amount), merchant, null);
    }

    private CardProfile profile(long avg, int count) {
        return CardProfile.builder()
                .cardNumber(CARD)
                .avgAmount(BigDecimal.valueOf(avg))
                .maxAmount(BigDecimal.valueOf(avg * 3))
                .transactionCount(count)
                .periodDays(90)
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    private FdsContext ctx(LocalDateTime now, long amount, String merchant,
                           List<TransactionRecord> history, CardProfile profile) {
        return FdsContext.builder()
                .cardNum(CARD).amount(BigDecimal.valueOf(amount)).merchantId(merchant)
                .now(now).history(history).profile(profile).build();
    }

    @Test
    @DisplayName("평범한 거래는 점수 0으로 승인된다")
    void normalTransaction() {
        FdsEvaluation result = engine.evaluate(ctx(NOON, 45_000, "M1", List.of(), profile(45_000, 128)));

        assertThat(result.getDecision()).isEqualTo(FdsDecision.APPROVE);
        assertThat(result.getRiskScore()).isZero();
        assertThat(result.getHits()).isEmpty();
    }

    @Test
    @DisplayName("같은 가맹점에서 연속 결제해도 가맹점 분산 룰에는 걸리지 않는다")
    void repeatedAtSameMerchant_notDiversityHit() {
        List<TransactionRecord> history = List.of(
                tx(NOON.minusMinutes(1), 30_000, "M1"),
                tx(NOON.minusMinutes(2), 30_000, "M1"));

        FdsEvaluation result = engine.evaluate(ctx(NOON, 30_000, "M1", history, profile(45_000, 128)));

        assertThat(result.hitRuleNames()).doesNotContain("MERCHANT_DIVERSITY");
    }

    @Test
    @DisplayName("1분 내 4건은 velocity로 적발되지만 단독으로는 차단하지 않는다")
    void velocityCount_aloneIsReview() {
        // 정상 고객도 짧은 시간에 여러 건 결제할 수 있다(나눠 결제 등).
        // 단일 신호만으로 차단하면 오탐 비용이 크므로 검토 대상으로만 표시한다.
        List<TransactionRecord> history = List.of(
                tx(NOON.minusSeconds(10), 50_000, "M1"),
                tx(NOON.minusSeconds(20), 50_000, "M1"),
                tx(NOON.minusSeconds(30), 50_000, "M1"));

        FdsEvaluation result = engine.evaluate(ctx(NOON, 50_000, "M1", history, profile(45_000, 128)));

        assertThat(result.hitRuleNames()).contains("VELOCITY_COUNT");
        assertThat(result.getDecision()).isEqualTo(FdsDecision.REVIEW);
    }

    @Test
    @DisplayName("소액을 짧은 시간에 반복하면 velocity와 카드테스팅이 함께 걸려 차단된다")
    void cardTestingAttack_blocks() {
        // 유출된 카드번호가 살아있는지 확인하는 전형적인 공격 패턴
        List<TransactionRecord> history = List.of(
                tx(NOON.minusSeconds(10), 1_000, "M1"),
                tx(NOON.minusSeconds(20), 1_000, "M1"),
                tx(NOON.minusSeconds(30), 1_000, "M1"));

        FdsEvaluation result = engine.evaluate(ctx(NOON, 1_000, "M1", history, profile(45_000, 128)));

        assertThat(result.hitRuleNames()).contains("VELOCITY_COUNT").contains("CARD_TESTING");
        assertThat(result.getDecision()).isEqualTo(FdsDecision.BLOCK);
    }

    @Test
    @DisplayName("소액 반복은 카드 유효성 확인 공격으로 잡는다")
    void cardTesting_detected() {
        List<TransactionRecord> history = List.of(
                tx(NOON.minusSeconds(10), 1_000, "M1"),
                tx(NOON.minusSeconds(20), 1_000, "M1"));

        FdsEvaluation result = engine.evaluate(ctx(NOON, 1_000, "M1", history, profile(45_000, 128)));

        assertThat(result.hitRuleNames()).contains("CARD_TESTING");
    }

    @Test
    @DisplayName("여러 가맹점을 짧은 시간에 옮겨 다니면 적발된다")
    void merchantDiversity_detected() {
        List<TransactionRecord> history = List.of(
                tx(NOON.minusMinutes(1), 50_000, "M1"),
                tx(NOON.minusMinutes(2), 50_000, "M2"));

        FdsEvaluation result = engine.evaluate(ctx(NOON, 50_000, "M3", history, profile(45_000, 128)));

        assertThat(result.hitRuleNames()).contains("MERCHANT_DIVERSITY");
    }

    @Test
    @DisplayName("평소 평균 대비 과도한 금액은 프로파일 기준으로 적발된다")
    void amountAnomaly_usesProfileNotRecentHistory() {
        // 최근 이력이 전혀 없어도 프로파일만으로 판정할 수 있어야 한다
        FdsEvaluation result = engine.evaluate(ctx(NOON, 500_000, "M1", List.of(), profile(45_000, 128)));

        assertThat(result.hitRuleNames()).contains("AMOUNT_ANOMALY");
    }

    @Test
    @DisplayName("프로파일 표본이 부족하면 금액 이상 판정을 보류한다")
    void amountAnomaly_skippedWhenProfileThin() {
        FdsEvaluation result = engine.evaluate(ctx(NOON, 500_000, "M1", List.of(), profile(50_000, 2)));

        assertThat(result.hitRuleNames()).doesNotContain("AMOUNT_ANOMALY");
    }

    @Test
    @DisplayName("프로파일이 없는 신규 카드는 금액 이상 판정을 하지 않는다")
    void amountAnomaly_skippedWhenNoProfile() {
        FdsEvaluation result = engine.evaluate(ctx(NOON, 5_000_000, "M1", List.of(), null));

        assertThat(result.hitRuleNames()).doesNotContain("AMOUNT_ANOMALY");
    }

    @Test
    @DisplayName("약한 신호도 겹치면 차단 구간으로 올라간다")
    void weakSignalsCombine_toBlock() {
        // 심야(25) + 금액이상(30) + 가맹점분산(40) = 95 → BLOCK
        List<TransactionRecord> history = List.of(
                tx(MIDNIGHT.minusMinutes(1), 50_000, "M1"),
                tx(MIDNIGHT.minusMinutes(2), 50_000, "M2"));

        FdsEvaluation result = engine.evaluate(
                ctx(MIDNIGHT, 1_500_000, "M3", history, profile(45_000, 128)));

        assertThat(result.getRiskScore()).isGreaterThanOrEqualTo(props.getBlockThreshold());
        assertThat(result.getDecision()).isEqualTo(FdsDecision.BLOCK);
        assertThat(result.getHits()).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("중간 점수는 차단하지 않고 검토 대상으로 표시한다")
    void mediumScore_isReview() {
        // 가맹점 분산(40)만 적발 → REVIEW
        List<TransactionRecord> history = List.of(
                tx(NOON.minusMinutes(1), 40_000, "M1"),
                tx(NOON.minusMinutes(2), 40_000, "M2"));

        FdsEvaluation result = engine.evaluate(ctx(NOON, 40_000, "M3", history, profile(45_000, 128)));

        assertThat(result.getDecision()).isEqualTo(FdsDecision.REVIEW);
        assertThat(result.getRiskScore())
                .isBetween(props.getReviewThreshold(), props.getBlockThreshold() - 1);
    }

    @Test
    @DisplayName("적발 사유를 설명할 수 있어야 한다")
    void explainsWhy() {
        List<TransactionRecord> history = List.of(
                tx(NOON.minusSeconds(10), 10_000, "M1"),
                tx(NOON.minusSeconds(20), 10_000, "M1"),
                tx(NOON.minusSeconds(30), 10_000, "M1"));

        FdsEvaluation result = engine.evaluate(ctx(NOON, 10_000, "M1", history, profile(45_000, 128)));

        assertThat(result.primaryReason()).isNotBlank();
        assertThat(result.getHits()).allSatisfy(hit -> {
            assertThat(hit.getReason()).isNotBlank();
            assertThat(hit.getScore()).isPositive();
        });
    }
}
