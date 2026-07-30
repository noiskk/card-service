package com.card.fds.rule;

import com.card.fds.entity.CardProfile;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 룰 평가에 필요한 입력 묶음.
 * 룰이 저장소를 직접 뒤지지 않고 이 컨텍스트만 보게 해서, 룰을 단위 테스트하기 쉽게 만든다.
 *
 * 두 종류의 과거 데이터를 담는다:
 *  - history : 분 단위 단기 이력 (인메모리) — "지금 몰아치는가"
 *  - profile : 배치가 집계한 장기 사용 패턴 (DB) — "이 사람답지 않은가"
 */
@Getter
@Builder
public class FdsContext {

    private final String cardNum;
    private final BigDecimal amount;
    private final String merchantId;
    private final String idempotencyKey;
    private final LocalDateTime now;

    /** 이번 거래를 제외한 해당 카드의 최근 단기 이력 */
    private final List<TransactionRecord> history;

    /** 카드별 장기 사용 프로파일. 신규 카드 등 프로파일이 없으면 null */
    private final CardProfile profile;

    /** 최근 minutes 분 이내의 이력 */
    public List<TransactionRecord> within(int minutes) {
        LocalDateTime from = now.minusMinutes(minutes);
        return history.stream().filter(r -> r.getAt().isAfter(from)).toList();
    }
}
