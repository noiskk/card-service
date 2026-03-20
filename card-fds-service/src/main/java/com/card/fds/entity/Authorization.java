package com.card.payment.entity;

import com.card.fds.entity.AuthorizationStatus;
import com.card.fds.entity.Card;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 승인 엔티티
 * 카드 승인 요청 및 응답 정보를 저장합니다.
 */
@Entity
@Table(name = "authorizations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Authorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 거래 고유 번호
     */
    @Column(name = "transaction_id", unique = true, nullable = false)
    private String transactionId;

    /**
     * 카드 테이블 FK 참조
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    /**
     * 거래 금액
     */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /**
     * 가맹점 ID
     */
    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    /**
     * 응답 코드 (00: 승인, 14: 카드정지, 51: 잔액부족, 54: 유효기간만료, 55: PIN오류, 61: 한도초과, 94: 중복거래)
     */
    @Column(nullable = false, length = 2)
    private String responseCode;

    /**
     * 승인 상태 (APPROVED: 승인, REJECTED: 거절, CONFIRMED: 매출확정)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AuthorizationStatus status;

    /**
     * 생성 일시
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 승인 일시
     */
    @CreatedDate
    @Column(name = "authorization_date", nullable = false, updatable = false)
    private LocalDateTime authorizationDate;
}