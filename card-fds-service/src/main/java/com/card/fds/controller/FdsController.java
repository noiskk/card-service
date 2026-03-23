package com.card.fds.controller;

import com.card.fds.client.PaymentFeignClient;
import com.card.fds.dto.FdsRequestDto;
import com.card.fds.service.FdsInspectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fds")
@RequiredArgsConstructor
@Slf4j
public class FdsController {

    private final FdsInspectionService fdsInspectionService;
    private final PaymentFeignClient paymentFeignClient;

    @PostMapping("/inspect")
    public ResponseEntity<?> inspect(@RequestBody FdsRequestDto request) {

        // 1. FDS 메인 검증 수행 및 DB에서 '진짜' 카드 타입 획득
        String realCardType = fdsInspectionService.inspect(request);

        // 2. 반환 객체 생성
        FdsRequestDto updatedRequest = FdsRequestDto.builder()
                .cardNum(request.getCardNum())
                .amount(request.getAmount())
                .merchantId(request.getMerchantId())
                .cardType(realCardType)
                .build();

        log.info("[FDS -> PAYMENT] 요청 이관: cardNum={}, cardType={}",
                updatedRequest.getCardNum(), updatedRequest.getCardType());

        // 3. 보정이 완료된 새로운 요청 객체를 PAYMENT 서비스로 이관
        return paymentFeignClient.processPayment(updatedRequest);
    }
}