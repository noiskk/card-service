package com.card.fds.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 위험 점수가 차단 임계치를 넘어 거절함을 나타내는 예외.
 *
 * 차단은 이상거래 판정 결과이므로 시스템 오류가 아니다 → HTTP 200 + 응답코드 94.
 * 어떤 룰이 몇 점으로 걸렸는지 함께 실어 보내 호출자와 운영자가 근거를 알 수 있게 한다.
 */
@Getter
public class SuspiciousTransactionException extends BusinessException {

    private final int riskScore;
    private final String hitRules;

    public SuspiciousTransactionException(int riskScore, String hitRules, String reason) {
        super("이상거래로 차단되었습니다" + (reason != null ? " (" + reason + ")" : ""),
                "94", HttpStatus.OK);
        this.riskScore = riskScore;
        this.hitRules = hitRules;
    }
}
