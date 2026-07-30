package com.card.fds.exception;

import com.card.fds.dto.FdsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<FdsResponse> handleBusiness(BusinessException ex) {
        log.warn("FDS 차단(비즈니스 사유): code={}, msg={}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(toResponse(ex));
    }

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<FdsResponse> handleSystem(SystemException ex) {
        log.error("FDS 처리 중 시스템 오류: code={}, msg={}", ex.getErrorCode(), ex.getMessage(), ex);
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
    public ResponseEntity<FdsResponse> handleUnknown(Exception ex) {
        log.error("예상하지 못한 오류", ex);
        FdsResponse response = FdsResponse.builder()
                .success(false)
                .responseCode("96")
                .message("시스템 오류가 발생했습니다")
                .build();
        return ResponseEntity.internalServerError().body(response);
    }

    private FdsResponse toResponse(DomainException ex) {
        return FdsResponse.builder()
                .success(false)
                .responseCode(ex.getErrorCode())
                .message(ex.getMessage())
                .build();
    }
}
