package com.card.fds.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 카드별 사용 프로파일 — "이 카드는 평소 어떻게 쓰이는가".
 *
 * 실시간 판정 경로에서 수개월치 거래를 매번 집계할 수는 없으므로,
 * 야간 배치가 승인 원장에서 미리 집계해 이 테이블에 적재하고 FDS는 조회만 한다.
 * (이 프로젝트에서는 배치를 만들지 않고 시드 데이터로 대체했다 — README에 명시)
 *
 * velocity 계열이 보는 단기 이력(인메모리, 분 단위)과는 저장소도 갱신 주기도 다르다.
 * 단기 이력은 "지금 몰아치는가", 프로파일은 "이 사람답지 않은가"를 본다.
 */
@Entity
@Table(name = "card_profiles")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CardProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_number", unique = true, nullable = false)
    private String cardNumber;

    /** 집계 기간의 건당 평균 결제액 */
    @Column(name = "avg_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal avgAmount;

    /** 집계 기간의 최대 결제액 */
    @Column(name = "max_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal maxAmount;

    /** 집계에 사용된 거래 건수 — 적으면 평균을 신뢰할 수 없다 */
    @Column(name = "transaction_count", nullable = false)
    private int transactionCount;

    /** 집계 기간 (일) */
    @Column(name = "period_days", nullable = false)
    private int periodDays;

    /** 마지막 집계 시각 — 오래됐으면 판정 근거로 쓰기 어렵다 */
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
}
