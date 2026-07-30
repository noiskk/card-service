package com.card.fds.rule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 카드별 최근 거래 이력 저장소.
 *
 * velocity 룰은 "최근 N분 동안 무슨 일이 있었나"를 알아야 하므로 시간창 이력이 필요하다.
 * 실무에서는 Redis(TTL)로 두어 인스턴스 간에 공유하지만, 이 프로젝트는 인메모리로 단순화했다.
 * 이력이 유실되면 탐지 정확도가 일시적으로 떨어지지만 결제 정합성이 깨지지는 않는다
 * (반대로 멱등키는 유실되면 이중결제로 이어지므로 RDB에 둔다).
 */
@Component
@Slf4j
public class TransactionHistoryStore {

    /** 이 시간이 지난 이력은 어떤 룰도 보지 않으므로 정리 대상 */
    private static final int RETENTION_MINUTES = 30;
    private static final int MAX_PER_CARD = 100;

    private final Map<String, List<TransactionRecord>> byCard = new ConcurrentHashMap<>();

    /**
     * 거래를 이력에 남긴다.
     * 같은 멱등키가 이미 있으면 같은 결제의 재시도이므로 중복 집계하지 않는다
     * — 그러지 않으면 네트워크 재시도가 velocity 룰에 걸려 정당한 재시도가 차단된다.
     */
    public void record(TransactionRecord record) {
        byCard.compute(record.getCardNum(), (card, history) -> {
            List<TransactionRecord> list = (history != null) ? history : new ArrayList<>();

            boolean isRetry = record.getIdempotencyKey() != null
                    && list.stream().anyMatch(r -> record.getIdempotencyKey().equals(r.getIdempotencyKey()));
            if (isRetry) {
                log.debug("같은 멱등키 재시도 — 이력에 중복 집계하지 않음: {}", record.getIdempotencyKey());
                return list;
            }

            list.add(record);
            evict(list, record.getAt());
            return list;
        });
    }

    /** 지정한 시각 이후의 거래만 (해당 카드) */
    public List<TransactionRecord> since(String cardNum, LocalDateTime from) {
        List<TransactionRecord> history = byCard.get(cardNum);
        if (history == null) {
            return List.of();
        }
        synchronized (history) {
            return history.stream()
                    .filter(r -> r.getAt().isAfter(from))
                    .sorted(Comparator.comparing(TransactionRecord::getAt))
                    .toList();
        }
    }

    /** 해당 카드의 보관 중인 전체 이력 */
    public List<TransactionRecord> all(String cardNum) {
        List<TransactionRecord> history = byCard.get(cardNum);
        if (history == null) {
            return List.of();
        }
        synchronized (history) {
            return List.copyOf(history);
        }
    }

    public void clear() {
        byCard.clear();
    }

    private void evict(List<TransactionRecord> list, LocalDateTime now) {
        LocalDateTime cutoff = now.minusMinutes(RETENTION_MINUTES);
        list.removeIf(r -> r.getAt().isBefore(cutoff));
        while (list.size() > MAX_PER_CARD) {
            list.remove(0);
        }
    }
}
