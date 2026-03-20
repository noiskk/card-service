package com.card.fds.controller;

import com.card.fds.client.PaymentFeignClient;
import com.card.fds.dto.FdsRequestDto;
import com.card.fds.service.FdsInspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/fds")
@RequiredArgsConstructor
public class FdsController {

    private final FdsInspectionService fdsInspectionService;
    private final PaymentFeignClient paymentFeignClient;

    @PostMapping("/inspect")
    public ResponseEntity<?> inspect(@RequestBody FdsRequestDto request) {

        // FDS 메인 검증 (3초 룰 & 상태 검사) - 실패 시 ExceptionHandler가 낚아채서 403 반환
        fdsInspectionService.inspect(request);

        // 검증 통과 시, FeignClient를 통해 CARD-PAYMENT 서비스로 요청 이관
        // PAYMENT 서비스의 응답(200 OK 등)을 그대로 VAN(클라이언트)에게 전달합니다.
        System.out.println("request = " + request);
        return paymentFeignClient.processPayment(request);
    }
}