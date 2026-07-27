package com.card.ledger.batch;

import com.card.ledger.entity.AuthorizationStatus;
import com.card.ledger.entity.Settlement;
import com.card.ledger.repository.AuthorizationRepository;
import com.card.ledger.repository.SettlementRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 가맹점 하나의 정산 금액을 계산한다.
 *
 * 승인 원장에서 해당 영업일 승인분을 모아 수수료를 떼고 지급액을 만든다.
 * 이미 정산된 가맹점은 null을 반환해 걸러낸다 — 배치를 두 번 돌려도 중복 정산되지 않게(정확히 한 번).
 */
@Component
@StepScope
@Slf4j
public class SettlementProcessor implements ItemProcessor<String, Settlement> {

    private final AuthorizationRepository authorizationRepository;
    private final SettlementRepository settlementRepository;
    private final MerchantFeePolicy feePolicy;
    private final LocalDate targetDate;

    public SettlementProcessor(AuthorizationRepository authorizationRepository,
                               SettlementRepository settlementRepository,
                               MerchantFeePolicy feePolicy,
                               @Value("#{jobParameters['targetDate']}") String targetDate) {
        this.authorizationRepository = authorizationRepository;
        this.settlementRepository = settlementRepository;
        this.feePolicy = feePolicy;
        this.targetDate = LocalDate.parse(targetDate);
    }

    @Override
    public Settlement process(String merchantId) {
        if (settlementRepository.findByMerchantIdAndSettlementDate(merchantId, targetDate).isPresent()) {
            log.info("이미 정산됨 - 가맹점: {}, 정산일: {}", merchantId, targetDate);
            return null; // null을 반환하면 writer로 넘어가지 않는다
        }

        LocalDateTime from = targetDate.atStartOfDay();
        LocalDateTime to = targetDate.plusDays(1).atStartOfDay();

        Long totalAmount = authorizationRepository.sumAmountByMerchant(
                merchantId, AuthorizationStatus.APPROVED, from, to);
        long count = authorizationRepository.countByMerchant(
                merchantId, AuthorizationStatus.APPROVED, from, to);

        if (totalAmount == null || totalAmount == 0L) {
            return null;
        }

        BigDecimal feeRate = feePolicy.rateFor(merchantId);
        // 수수료는 원 단위로 절사한다(가맹점에 유리한 방향으로 반올림하지 않는다)
        long feeAmount = BigDecimal.valueOf(totalAmount)
                .multiply(feeRate)
                .setScale(0, RoundingMode.DOWN)
                .longValue();

        Settlement settlement = Settlement.builder()
                .merchantId(merchantId)
                .settlementDate(targetDate)
                .transactionCount((int) count)
                .totalAmount(totalAmount)
                .feeRate(feeRate)
                .feeAmount(feeAmount)
                .payoutAmount(totalAmount - feeAmount)
                .build();

        log.info("정산 계산 - 가맹점: {}, 승인 {}건 {}원, 수수료 {}원, 지급 {}원",
                merchantId, count, totalAmount, feeAmount, settlement.getPayoutAmount());

        return settlement;
    }
}
