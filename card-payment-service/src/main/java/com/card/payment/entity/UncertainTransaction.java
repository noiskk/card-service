package com.card.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 결과가 불확실한 거래(망취소 대상).
 *
 * 은행 호출이 타임아웃되거나, 출금 후 원장 기록에 실패했는데 보상까지 실패한 경우처럼
 * "실제 상태를 우리가 모르는" 거래를 여기에 남겨두고 대사 배치가 나중에 정리한다.
 */
@Entity
@Table(name = "uncertain_transactions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class UncertainTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", unique = true, nullable = false)
    private String transactionId;

    @Column(name = "card_number", nullable = false)
    private String cardNumber;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "merchant_id")
    private String merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private UncertainReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReconciliationStatus status;

    /** 대사 결과 요약 (예: "은행에 출금 기록 없음 - 종결", "취소 완료") */
    @Column(name = "resolution", length = 500)
    private String resolution;

    /** 배치가 시도한 횟수 */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** 대사 완료 처리 */
    public void resolve(String resolution) {
        this.status = ReconciliationStatus.RESOLVED;
        this.resolution = resolution;
        this.resolvedAt = LocalDateTime.now();
        this.attemptCount++;
    }

    /** 대사 실패 처리 (수동 확인 대상) */
    public void fail(String resolution) {
        this.status = ReconciliationStatus.FAILED;
        this.resolution = resolution;
        this.attemptCount++;
    }

    /** 이번엔 정리하지 못했지만 다음 배치에서 다시 시도한다 (PENDING 유지) */
    public void retryLater(String resolution) {
        this.resolution = resolution;
        this.attemptCount++;
    }
}
