package com.card.fds.rule;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * velocity 계열 룰이 참조하는 과거 거래 한 건.
 */
@Getter
@RequiredArgsConstructor
public class TransactionRecord {
    private final LocalDateTime at;
    private final String cardNum;
    private final BigDecimal amount;
    private final String merchantId;
    /** 멱등키 — 같은 키의 재시도를 이력에서 중복 집계하지 않기 위해 보관한다 */
    private final String idempotencyKey;
}
