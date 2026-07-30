package com.card.fds.service;

import com.card.fds.dto.FdsRequestDto;
import com.card.fds.entity.Card;
import com.card.fds.entity.CardStatus;
import com.card.fds.exception.CardNotFoundException;
import com.card.fds.exception.InactiveCardException;
import com.card.fds.exception.SuspiciousTransactionException;
import com.card.fds.repository.CardInfoReadOnlyRepo;
import com.card.fds.repository.CardProfileReadOnlyRepo;
import com.card.fds.rule.FdsContext;
import com.card.fds.rule.FdsEvaluation;
import com.card.fds.rule.FdsRuleEngine;
import com.card.fds.rule.TransactionHistoryStore;
import com.card.fds.rule.TransactionRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 이상거래 판정.
 *
 * 두 단계로 나뉜다:
 *  1. 확정 조건 — 카드가 없거나 정지 상태면 점수를 볼 필요 없이 즉시 거절한다(사실 판단).
 *  2. 위험 점수 — 그 외는 룰 엔진이 점수를 합산해 승인/검토/차단을 판정한다(확률 판단).
 *
 * 확정 조건과 점수 판정을 섞지 않는 이유: 정지 카드를 "점수 100점"으로 처리하면
 * 다른 룰과 합산되면서 임계치 조정에 영향을 받는다. 확정된 사실은 임계치와 무관해야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FdsInspectionService {

    private final CardInfoReadOnlyRepo cardRepo;
    private final CardProfileReadOnlyRepo profileRepo;
    private final FdsRuleEngine ruleEngine;
    private final TransactionHistoryStore historyStore;

    public FdsInspectionResult inspect(FdsRequestDto request) {
        String cardNum = request.getCardNum();
        log.info("[FDS 판정 시작] 카드번호: {}, 금액: {}", cardNum, request.getAmount());

        // 1단계 — 확정 조건
        Card card = checkCardStatus(cardNum);

        // 2단계 — 위험 점수
        LocalDateTime now = LocalDateTime.now();
        FdsContext context = FdsContext.builder()
                .cardNum(cardNum)
                .amount(request.getAmount())
                .merchantId(request.getMerchantId())
                .idempotencyKey(request.getIdempotencyKey())
                .now(now)
                .history(historyStore.all(cardNum))
                // 장기 프로파일은 배치가 적재한 것을 조회만 한다. 없으면(신규 카드) null.
                .profile(profileRepo.findByCardNumber(cardNum).orElse(null))
                .build();

        FdsEvaluation evaluation = ruleEngine.evaluate(context);

        // 판정과 무관하게 이력에 남긴다 — 차단된 시도도 다음 판정의 근거가 된다.
        // 같은 멱등키의 재시도는 저장소가 중복 집계하지 않는다.
        historyStore.record(new TransactionRecord(
                now, cardNum, request.getAmount(), request.getMerchantId(), request.getIdempotencyKey()));

        if (evaluation.isBlocked()) {
            log.warn("[FDS 차단] 카드: {}, 점수: {}, 적발: {}",
                    cardNum, evaluation.getRiskScore(), evaluation.hitRuleNames());
            throw new SuspiciousTransactionException(
                    evaluation.getRiskScore(), evaluation.hitRuleNames(), evaluation.primaryReason());
        }

        log.info("[FDS 통과] 카드: {}, 판정: {}, 점수: {}, 카드타입: {}",
                cardNum, evaluation.getDecision(), evaluation.getRiskScore(), card.getCardType());

        return new FdsInspectionResult(card.getCardType().toString(), evaluation);
    }

    /**
     * 카드 존재·상태 검증. 확정된 사실이므로 점수 판정 대상이 아니다.
     */
    private Card checkCardStatus(String cardNum) {
        Card card = cardRepo.findByCardNumber(cardNum)
                .orElseThrow(() -> new CardNotFoundException(cardNum));

        if (card.getCardStatus() != CardStatus.ACTIVE) {
            log.warn("[FDS 차단] 비활성 카드 결제 시도 - 상태: {}", card.getCardStatus());
            throw new InactiveCardException(card.getCardStatus().toString());
        }
        return card;
    }

    /** 판정 통과 결과 — 실제 카드 타입과 위험 평가를 함께 돌려준다 */
    public record FdsInspectionResult(String cardType, FdsEvaluation evaluation) {
    }
}
