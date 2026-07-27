package com.card.ledger.entity;

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
 * 승인 원장 (INSERT-only, 불변). 생성 후 어떤 필드도 수정하지 않는다.
 * 취소·매출확정 등 상태 변화는 UPDATE가 아니라 새 원장 레코드로 기록한다.
 */
@Entity
@Table(name = "authorizations")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class Authorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", unique = true, nullable = false)
    private String transactionId;

    // 다른 서비스(카드)의 엔티티를 FK로 참조하지 않는다(서비스 경계). 카드번호를 값으로 저장.
    @Column(name = "card_number", nullable = false)
    private String cardNumber;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(name = "response_code", nullable = false, length = 2)
    private String responseCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AuthorizationStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
