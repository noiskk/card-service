package com.card.fds.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 최근 판정 이력 (관제 화면 표시용).
 * 감사 목적의 저장소가 아니라 시연·디버깅을 위한 인메모리 버퍼라 재기동하면 사라진다.
 * (판정 로직이 참조하는 거래 이력은 별도로 TransactionHistoryStore가 관리한다)
 */
@Component
@Getter
public class FdsHistory {

    private static final int MAX = 50;
    private final Deque<Entry> entries = new ConcurrentLinkedDeque<>();

    public void record(String cardNum, BigDecimal amount, String code, String message, boolean passed,
                       String decision, int riskScore, String hitRules) {
        entries.addFirst(new Entry(LocalDateTime.now(), cardNum, amount, code, message, passed,
                decision, riskScore, hitRules));
        while (entries.size() > MAX) {
            entries.pollLast();
        }
    }

    public List<Entry> recent() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public void clear() {
        entries.clear();
    }

    @Getter
    @RequiredArgsConstructor
    public static class Entry {
        private final LocalDateTime at;
        private final String cardNum;
        private final BigDecimal amount;
        private final String responseCode;
        private final String message;
        private final boolean passed;
        /** APPROVE / REVIEW / BLOCK */
        private final String decision;
        private final int riskScore;
        private final String hitRules;
    }
}
