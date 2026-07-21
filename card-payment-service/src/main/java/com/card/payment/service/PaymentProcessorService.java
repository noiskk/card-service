package com.card.payment.service;

import com.card.payment.dto.PaymentRequest;
import com.card.payment.dto.PaymentResponse;
import com.card.payment.entity.IdempotencyRecord;
import com.card.payment.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 결제 멱등성 오케스트레이터.
 * 트랜잭션을 직접 걸지 않고, 조회 → 예약 → 실행 → 완료 4단계를 조율한다.
 * (트랜잭션 경계는 IdempotencyService(REQUIRES_NEW)와 PaymentExecutor(@Transactional)가 각자 담당.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessorService {

    private final IdempotencyService idempotencyService;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final PaymentExecutor paymentExecutor;

    public PaymentResponse process(PaymentRequest request) {
        String key = request.getIdempotencyKey();

        // 1. 조회: 이미 처리(중)인 키면 저장된 결과 반환 (출금 안 함)
        Optional<IdempotencyRecord> existing = idempotencyRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            log.info("멱등키 재시도 감지 - key={}, status={}", key, existing.get().getStatus());
            return toResponse(existing.get());
        }

        // 2. 예약: PENDING 선점 (동시 재시도는 여기서 UNIQUE 위반으로 걸러짐)
        try {
            idempotencyService.reserve(key);
        } catch (DataIntegrityViolationException e) {
            log.info("멱등키 동시 선점 감지 - key={}", key);
            return toResponse(idempotencyRepository.findByIdempotencyKey(key).orElseThrow());
        }

        // 3. 실제 결제 실행 (별도 빈이라 트랜잭션 경계 유효)
        PaymentResponse result;
        try {
            result = paymentExecutor.execute(request);
        } catch (RuntimeException e) {
            // 실행이 예외로 실패하면 완료 기록을 못 남기므로, 예약을 해제해
            // PENDING 레코드가 재시도를 오염시키지 않게 한다.
            idempotencyService.remove(key);
            throw e;
        }

        // 4. 완료 기록 (PENDING → COMPLETED + 결과 저장)
        idempotencyService.complete(key, result);
        return result;
    }

    private PaymentResponse toResponse(IdempotencyRecord record) {
        return PaymentResponse.builder()
                .transactionId(record.getTransactionId())
                .responseCode(record.getResponseCode())
                .message(record.getMessage())
                .amount(record.getAmount())
                .success(record.isSuccess())
                .build();
    }
}