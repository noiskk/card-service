package com.card.payment.repository;

import com.card.payment.entity.ReconciliationStatus;
import com.card.payment.entity.UncertainTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UncertainTransactionRepository extends JpaRepository<UncertainTransaction, Long> {

    Optional<UncertainTransaction> findByTransactionId(String transactionId);

    List<UncertainTransaction> findByStatus(ReconciliationStatus status);
}
