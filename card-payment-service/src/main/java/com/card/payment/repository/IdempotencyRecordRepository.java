package com.card.payment.repository;

import com.card.payment.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 멱등성 레코드 리포지토리.
 * 멱등키로 "이미 처리했거나 처리 중인 요청인지" 조회한다.
 */
@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
    void deleteByIdempotencyKey(String idempotencyKey);
}
