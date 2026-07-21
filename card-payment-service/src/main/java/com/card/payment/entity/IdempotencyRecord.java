package com.card.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 멱등성 전용 테이블.
 * idempotencyKey로 "이미 처리한 요청인지" 판별하고, 처리 결과를 보관해 재시도 시 그대로 반환한다.
 * (실제 승인 이력은 append-only인 Authorization 원장이 따로 담당한다.)
 */
@Entity
@Table(name = "idempotency_records")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 클라이언트(POS)가 발급한 멱등키. UNIQUE 제약이 동시 재시도의 이중 선점을 막는다.
    @Column(name = "idempotency_key", unique = true, nullable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyStatus status;

    // 저장된 결과 (재시도 시 이걸 그대로 반환)
    private String transactionId;
    private String responseCode;
    private String message;
    private Long amount;
    private boolean success;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * PENDING 상태 처리 결과를 채워 COMPLETED로 전환한다.
     * (멱등성 테이블은 원장과 달리 상태 변경(UPDATE)이 허용된다.)
     */
    public void complete(String transactionId, String responseCode, String message,
                         Long amount, boolean success) {
        this.status = IdempotencyStatus.COMPLETED;
        this.transactionId = transactionId;
        this.responseCode = responseCode;
        this.message = message;
        this.amount = amount;
        this.success = success;
    }
}
