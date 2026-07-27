package com.card.ledger.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 가맹점별 일 정산 결과.
 *
 * 수수료율을 "그때 적용한 값"으로 함께 저장한다. 나중에 요율이 바뀌어도
 * 과거 정산이 왜 그 금액이었는지 다시 계산해 확인할 수 있어야 하기 때문이다.
 */
@Entity
@Table(
        name = "settlements",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_settlement_merchant_date",
                columnNames = {"merchant_id", "settlement_date"})
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    /** 정산 대상 영업일 */
    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    /** 승인 건수 */
    @Column(name = "transaction_count", nullable = false)
    private int transactionCount;

    /** 총 승인 금액 */
    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    /** 적용 수수료율 스냅샷 (예: 0.0230) */
    @Column(name = "fee_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal feeRate;

    /** 수수료 (원 단위 절사) */
    @Column(name = "fee_amount", nullable = false)
    private Long feeAmount;

    /** 가맹점에 실제 지급할 금액 */
    @Column(name = "payout_amount", nullable = false)
    private Long payoutAmount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
