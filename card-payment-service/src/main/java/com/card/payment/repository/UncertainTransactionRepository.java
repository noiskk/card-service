package com.card.payment.repository;

import com.card.payment.entity.ReconciliationStatus;
import com.card.payment.entity.UncertainTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UncertainTransactionRepository extends JpaRepository<UncertainTransaction, Long> {

    Optional<UncertainTransaction> findByTransactionId(String transactionId);

    List<UncertainTransaction> findByStatus(ReconciliationStatus status);

    /**
     * 대사 배치용 조회. 페이지 번호가 아니라 마지막 id를 커서로 쓴다(keyset paging).
     *
     * 배치가 처리한 건은 PENDING에서 빠져나가므로, 페이지 번호로 넘기면
     * 결과 집합이 줄어들면서 뒤 페이지가 앞으로 당겨져 일부 건을 건너뛰게 된다.
     */
    List<UncertainTransaction> findByStatusAndIdGreaterThanOrderByIdAsc(
            ReconciliationStatus status, Long lastId, Pageable pageable);
}
