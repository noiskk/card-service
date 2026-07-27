package com.card.ledger.repository;

import com.card.ledger.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findByMerchantIdAndSettlementDate(String merchantId, LocalDate settlementDate);

    List<Settlement> findBySettlementDate(LocalDate settlementDate);
}
