package com.card.fds.service;

import com.card.fds.dto.FdsRequestDto;
import com.card.fds.entity.Card;
import com.card.fds.entity.CardStatus;
import com.card.fds.exception.FdsException; // 👈 우리가 만든 예외 임포트!
import com.card.fds.repository.CardInfoReadOnlyRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class FdsInspectionService {

    private final CardInfoReadOnlyRepo cardRepo;

    // Rule 1을 위한 In-Memory Session (카드번호를 Key로, 마지막 요청 시간을 Value로 저장)
    private final Map<String, LocalDateTime> sessionMemory = new ConcurrentHashMap<>();

    /**
     * FDS 메인 검증 로직
     * @return DB에서 확인한 '진짜' 카드 타입 (CREDIT or DEBIT)
     */
    public String inspect(FdsRequestDto request) {
        String cardNum = request.getCardNum();
        log.info("[FDS 검증 시작] 카드번호: {}", cardNum);

        // 단기 다발성 거래 검사 (Rule 1)
        checkSessionFrequency(cardNum);

        // 카드 활성화 상태 검사 (Rule 2)를 하면서 Card 엔티티를 반환
        Card card = checkCardStatus(cardNum);

        log.info("[FDS 검증 통과] 정상적인 요청입니다. 진짜 카드타입: {}", card.getCardType());

        return card.getCardType().toString();
    }

    private void checkSessionFrequency(String cardNum) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastRequestTime = sessionMemory.get(cardNum);

        if (lastRequestTime != null) {
            long secondsBetween = ChronoUnit.SECONDS.between(lastRequestTime, now);
            if (secondsBetween < 3) {
                log.warn("[FDS_BLOCKED] 3초 이내 다발성 거래 감지! ({}초 경과)", secondsBetween);
                throw new FdsException("FDS_BLOCKED: 단기 다발성 거래로 차단되었습니다.");
            }
        }
        sessionMemory.put(cardNum, now);
    }

    /**
     * [Rule 2] 카드 활성화 상태 검증
     */
    private Card checkCardStatus(String cardNum) {
        Card card = cardRepo.findByCardNumber(cardNum)
                .orElseThrow(() -> new FdsException("카드를 찾을 수 없습니다."));

        if (card.getCardStatus() != CardStatus.ACTIVE) {
            log.warn("[INVALID_STATE] 비활성 카드 결제 시도! 상태: {}", card.getCardStatus());
            throw new FdsException("INVALID_STATE: 사용 불가능한 카드입니다.");
        }

        return card;
    }
}