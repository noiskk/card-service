package com.card.ledger.exception;

import com.card.ledger.dto.LedgerErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<LedgerErrorResponse> handleBusiness(BusinessException ex) {
        log.warn("원장 요청 오류: code={}, msg={}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(
                LedgerErrorResponse.builder()
                        .success(false)
                        .errorCode(ex.getErrorCode())
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<LedgerErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .orElse("잘못된 요청입니다");
        log.warn("원장 요청 검증 실패: {}", msg);
        return ResponseEntity.badRequest().body(
                LedgerErrorResponse.builder()
                        .success(false)
                        .errorCode("INVALID_REQUEST")
                        .message(msg)
                        .build());
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
    public ResponseEntity<LedgerErrorResponse> handleUnknown(Exception ex) {
        log.error("예상하지 못한 오류", ex);
        return ResponseEntity.internalServerError().body(
                LedgerErrorResponse.builder()
                        .success(false)
                        .errorCode("SYSTEM_ERROR")
                        .message("시스템 오류가 발생했습니다")
                        .build());
    }
}
