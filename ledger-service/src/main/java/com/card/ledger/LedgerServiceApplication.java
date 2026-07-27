package com.card.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 원장 서비스 (카드사 내부 MSA).
 * 승인/거절 결과를 INSERT-only 불변 원장으로 기록·조회한다. 다른 서비스를 호출하지 않는 leaf.
 */
@EnableJpaAuditing
@SpringBootApplication
public class LedgerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LedgerServiceApplication.class, args);
    }
}
