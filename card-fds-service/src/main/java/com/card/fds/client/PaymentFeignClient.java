package com.card.fds.client;

import com.card.fds.dto.FdsRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * CARD-PAYMENT 서비스와 통신하는 Feign 클라이언트
 * url: 실제 PAYMENT 서비스가 띄워질 주소
 */
@FeignClient(name = "payment-service", url = "${payment.service.url:http://192.168.0.17:9091}")
public interface PaymentFeignClient {

    // PAYMENT 서비스의 결제 처리 API 엔드포인트
    @PostMapping("/api/card/payments/process")
    ResponseEntity<Object> processPayment(@RequestBody FdsRequestDto request);

}