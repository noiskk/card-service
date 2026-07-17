package com.card.payment.controller;

import com.card.payment.dto.PaymentRequest;
import com.card.payment.dto.PaymentResponse;
import com.card.payment.service.PaymentProcessorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/card/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment", description = "카드 결제 API")
public class TransactionController {

    private final PaymentProcessorService paymentProcessorService;

    @Operation(
            summary = "결제 요청 처리",
            description = "FDS 검증을 통과한 결제 요청을 수신하여 체크카드는 은행 출금, 신용카드는 한도 검사를 수행합니다."
    )
    @PostMapping("/process")
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody PaymentRequest
                                                                  request) {
        log.info("결제 요청 수신 - 카드번호: {}, 금액: {}, 가맹점: {}, 카드타입: {}",
                request.getCardNum(), request.getAmount(),
                request.getMerchantId(), request.getCardType());

        PaymentResponse response = paymentProcessorService.process(request);

        if (response.isSuccess()) {
            log.info("결제 성공 - 거래ID: {}", response.getTransactionId());
        } else {
            log.warn("결제 실패 - 거래ID: {}, 코드: {}",
                    response.getTransactionId(), response.getResponseCode());
        }

        return ResponseEntity.ok(response);
    }
}