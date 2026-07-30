package com.card.payment.exception;

import com.card.payment.dto.PaymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<PaymentResponse> handleBusiness(BusinessException ex){
        log.warn("결제 거절(비즈니스 사유): code={}, msg={}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(toResponse(ex));
    }

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<PaymentResponse> handleSystem(SystemException ex){
        log.error("결제 처리 중 시스템 오류: code={}, msg={}", ex.getErrorCode(), ex.getMessage(), ex);
        return ResponseEntity.status(ex.getHttpStatus()).body(toResponse(ex));
    }

    /**
     * 없는 정적 리소스 요청(favicon.ico 등)은 404로 끝낸다.
     * catch-all(Exception)에 걸리면 500 + ERROR 로그가 남아 실제 장애와 구분되지 않는다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<PaymentResponse> handleUnknown(Exception ex){
        log.error("예상하지 못한 오류", ex);
        PaymentResponse response = PaymentResponse.builder()
                .responseCode("96")
                .message("시스템 오류가 발생했습니다")
                .success(false)
                .processedAt(LocalDateTime.now())
                .build();
        return ResponseEntity.internalServerError().body(response);
    }

    private PaymentResponse toResponse(DomainException ex){
        return PaymentResponse.builder()
                .transactionId(ex.getTransactionId())
                .responseCode(ex.getErrorCode())
                .message(ex.getMessage())
                .amount(ex.getAmount())
                .success(false)
                .processedAt(LocalDateTime.now())
                .build();
    }
}
