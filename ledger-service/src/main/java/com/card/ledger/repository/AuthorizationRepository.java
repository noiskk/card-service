package com.card.ledger.repository;

import com.card.ledger.entity.Authorization;
import com.card.ledger.entity.AuthorizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuthorizationRepository extends JpaRepository<Authorization, Long> {

    Optional<Authorization> findByTransactionId(String transactionId);

    /** 정산 대상일에 승인이 있었던 가맹점 목록 */
    @Query("select distinct a.merchantId from Authorization a "
            + "where a.status = :status and a.createdAt >= :from and a.createdAt < :to "
            + "order by a.merchantId")
    List<String> findMerchantIdsWithApprovals(@Param("status") AuthorizationStatus status,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    /** 가맹점의 정산 대상일 승인 합계 (거절 건은 대금이 오가지 않으므로 제외) */
    @Query("select coalesce(sum(a.amount), 0) from Authorization a "
            + "where a.merchantId = :merchantId and a.status = :status "
            + "and a.createdAt >= :from and a.createdAt < :to")
    Long sumAmountByMerchant(@Param("merchantId") String merchantId,
                             @Param("status") AuthorizationStatus status,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to);

    @Query("select count(a) from Authorization a "
            + "where a.merchantId = :merchantId and a.status = :status "
            + "and a.createdAt >= :from and a.createdAt < :to")
    long countByMerchant(@Param("merchantId") String merchantId,
                         @Param("status") AuthorizationStatus status,
                         @Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to);
}
