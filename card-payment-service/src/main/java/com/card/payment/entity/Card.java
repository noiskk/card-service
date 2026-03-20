package com.card.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 카드 엔티티
 * 카드 정보를 저장하며, 신용카드와 체크카드를 모두 지원합니다.
 */
@Entity
@Table(name = "cards")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Card {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_number", unique = true, nullable = false)
    private String cardNumber;

    /**
     * 신용 한도 (신용카드 전용)
     */
    @Column(name = "credit_limit")
    private Long creditLimit;

    /**
     * 사용 금액 (신용카드 전용 - 누적 사용액)
     */
    @Column(name = "used_amount")
    private Long usedAmount;

    /**
     * 1회 결제 한도 (신용/체크 공통)
     */
    @Column(name = "per_transaction_limit")
    private Long perTransactionLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_status", nullable = false)
    private CardStatus cardStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 유효기간
     */
    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

}
