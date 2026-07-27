package com.card.fds.controller;

import com.card.fds.dto.FdsRequestDto;
import com.card.fds.dto.FdsResponse;
import com.card.fds.exception.DomainException;
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
            String realCardType = fdsInspectionService.inspect(request);
            fdsHistory.record(request.getCardNum(), request.getAmount(), "00", "정상 거래", true);

            log.info("[FDS 통과] cardNum={}, cardType={}", request.getCardNum(), realCardType);

            return ResponseEntity.ok(FdsResponse.builder()
                    .success(true)
                    .responseCode("00")
                    .message("정상 거래")
                    .cardType(realCardType)
                    .build());
        } catch (DomainException e) {
            // 차단 사유를 이력에 남기고 그대로 던진다. 응답 조립은 GlobalExceptionHandler가 한다.
            fdsHistory.record(request.getCardNum(), request.getAmount(), e.getErrorCode(), e.getMessage(), false);
            throw e;
        }
    }
}
