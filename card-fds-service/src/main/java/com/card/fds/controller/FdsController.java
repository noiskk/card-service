package com.card.fds.controller;

import com.card.fds.dto.FdsRequestDto;
import com.card.fds.dto.FdsResponse;
import com.card.fds.exception.DomainException;
import com.card.fds.exception.SuspiciousTransactionException;
import com.card.fds.rule.FdsEvaluation;
import com.card.fds.service.FdsHistory;
import com.card.fds.service.FdsInspectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이상거래 탐지(FDS) 엔드포인트.
 *
 * 사기 여부만 판정해 돌려주고 승인 흐름을 이어가지 않는다(leaf).
 * 승인 오케스트레이션은 card-payment-service가 담당한다.
 */
@RestController
@RequestMapping("/api/fds")
@RequiredArgsConstructor
@Slf4j
public class FdsController {

    private final FdsInspectionService fdsInspectionService;
    private final FdsHistory fdsHistory;

    @PostMapping("/inspect")
    public ResponseEntity<FdsResponse> inspect(@RequestBody FdsRequestDto request) {
        try {
            FdsInspectionService.FdsInspectionResult result = fdsInspectionService.inspect(request);
            FdsEvaluation evaluation = result.evaluation();

            fdsHistory.record(request.getCardNum(), request.getAmount(), "00",
                    describe(evaluation), true,
                    evaluation.getDecision().name(), evaluation.getRiskScore(), evaluation.hitRuleNames());

            return ResponseEntity.ok(FdsResponse.builder()
                    .success(true)
                    .responseCode("00")
                    .message(describe(evaluation))
                    .cardType(result.cardType())
                    .decision(evaluation.getDecision().name())
                    .riskScore(evaluation.getRiskScore())
                    .hitRules(evaluation.hitRuleNames())
                    .build());

        } catch (SuspiciousTransactionException e) {
            fdsHistory.record(request.getCardNum(), request.getAmount(), e.getErrorCode(),
                    e.getMessage(), false, "BLOCK", e.getRiskScore(), e.getHitRules());
            throw e;

        } catch (DomainException e) {
            // 카드 없음/정지 등 확정 조건 — 점수 판정 이전에 걸린 건이라 점수가 없다
            fdsHistory.record(request.getCardNum(), request.getAmount(), e.getErrorCode(),
                    e.getMessage(), false, "BLOCK", 0, "CARD_STATUS");
            throw e;
        }
    }

    private String describe(FdsEvaluation evaluation) {
        return switch (evaluation.getDecision()) {
            case APPROVE -> "정상 거래";
            // 승인은 하되 표시를 남긴다. 실무라면 추가 인증이나 사후 모니터링으로 넘어가는 구간.
            case REVIEW -> "검토 대상 (%d점: %s)".formatted(evaluation.getRiskScore(), evaluation.hitRuleNames());
            case BLOCK -> "차단";
        };
    }
}
