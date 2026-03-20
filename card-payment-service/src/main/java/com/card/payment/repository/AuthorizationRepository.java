package com.card.payment.repository;

import com.card.payment.entity.Authorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 승인 리포지토리
 * 승인 내역에 대한 데이터베이스 접근을 담당합니다.
 */
@Repository
public interface AuthorizationRepository extends JpaRepository<Authorization, Long> {
    
    /**
     * 거래 ID로 승인 내역 조회
     * @param transactionId 거래 고유 번호
     * @return 승인 내역
     */
    Optional<Authorization> findByTransactionId(String transactionId);
}
