package com.card.fds.rule;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 룰 임계치.
 *
 * 코드에 상수로 박지 않고 설정으로 빼는 이유: 실무에서 임계치는 오탐률을 보며 계속 조정된다.
 * 배포 없이 바꿀 수 있어야 하고, 어떤 값으로 판정했는지도 설명할 수 있어야 한다.
 */
@Component
@ConfigurationProperties(prefix = "fds.rule")
@Getter
@Setter
public class FdsRuleProperties {

    /** 판정 임계치 — 이 점수 이상이면 차단 */
    private int blockThreshold = 70;
    /** 이 점수 이상이면 승인하되 검토 대상으로 표시 */
    private int reviewThreshold = 40;

    // VELOCITY_COUNT — 짧은 시간에 반복 결제
    private int velocityWindowMinutes = 1;
    private int velocityMaxCount = 3;
    private int velocityScore = 60;

    // CARD_TESTING — 카드 유효성 확인용 소액 반복
    private int testingWindowMinutes = 1;
    private BigDecimal testingAmountUnder = new BigDecimal("10000");
    private int testingMaxCount = 2;
    private int testingScore = 50;

    // VELOCITY_AMOUNT — 짧은 시간 누적 금액
    private int amountWindowMinutes = 10;
    private BigDecimal amountMaxTotal = new BigDecimal("3000000");
    private int amountScore = 50;

    // MERCHANT_DIVERSITY — 짧은 시간에 여러 가맹점
    private int diversityWindowMinutes = 5;
    private int diversityMaxMerchants = 2;
    private int diversityScore = 40;

    // AMOUNT_ANOMALY — 평소 사용 패턴 대비 과도한 금액
    private int anomalyMinHistory = 3;
    private BigDecimal anomalyMultiplier = new BigDecimal("5");
    private int anomalyScore = 30;

    // MIDNIGHT_HIGH_AMOUNT — 심야 고액
    private int midnightFromHour = 0;
    private int midnightToHour = 6;
    private BigDecimal midnightAmountOver = new BigDecimal("1000000");
    private int midnightScore = 25;
}
