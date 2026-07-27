package com.card.ledger.batch;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 가맹점 수수료율 정책.
 * 실제 서비스라면 별도 테이블/설정 서비스에서 관리하지만, 여기서는 설정으로 둔다.
 * 중요한 건 "정산 시점에 적용한 요율을 결과와 함께 저장한다"는 것이다.
 */
@Component
@ConfigurationProperties(prefix = "settlement.fee")
@Getter
@Setter
public class MerchantFeePolicy {

    /** 기본 수수료율 (예: 0.023 = 2.3%) */
    private BigDecimal defaultRate = new BigDecimal("0.023");

    /** 가맹점별 개별 요율 */
    private Map<String, BigDecimal> rates = new HashMap<>();

    public BigDecimal rateFor(String merchantId) {
        return rates.getOrDefault(merchantId, defaultRate);
    }
}
