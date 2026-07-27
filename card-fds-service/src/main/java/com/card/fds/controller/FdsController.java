package com.card.fds.controller;

import com.card.fds.dto.FdsRequestDto;
import com.card.fds.dto.FdsResponse;
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

    @PostMapping("/inspect")
    public ResponseEntity<FdsResponse> inspect(@RequestBody FdsRequestDto request) {
        // 차단 사유는 예외로 던져지고 GlobalExceptionHandler가 200 + 응답코드로 변환한다.
        String realCardType = fdsInspectionService.inspect(request);

        log.info("[FDS 통과] cardNum={}, cardType={}", request.getCardNum(), realCardType);

        return ResponseEntity.ok(FdsResponse.builder()
                .success(true)
                .responseCode("00")
                .message("정상 거래")
                .cardType(realCardType)
                .build());
    }
}
