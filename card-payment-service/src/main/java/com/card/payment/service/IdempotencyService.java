package com.card.payment.service;

import com.card.payment.dto.PaymentResponse;
import com.card.payment.entity.IdempotencyRecord;
import com.card.payment.entity.IdempotencyStatus;
import com.card.payment.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멱등성 예약/완료 처리.
 * reserve/complete는 REQUIRES_NEW가 필요해 별도 빈으로 둔다(프록시 경유).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRepository;

    /**
     * 멱등키를 PENDING으로 선점한다.
     * 동시 재시도는 UNIQUE 제약 위반(DataIntegrityViolationException)으로 걸러진다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(String idempotencyKey) {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .status(IdempotencyStatus.PENDING)
                .build();
        idempotencyRepository.saveAndFlush(record); // 즉시 flush로 UNIQUE 위반을 이 자리에서 감지
    }

    /**
     * 예약 레코드에 결과를 채우고 COMPLETED로 전환한다(재시도 시 반환할 결과).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String idempotencyKey, PaymentResponse result) {
        IdempotencyRecord record = idempotencyRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("예약되지 않은 멱등키: " + idempotencyKey));

        record.complete(
                result.getTransactionId(),
                result.getResponseCode(),
                result.getMessage(),
                result.getAmount(),
                result.isSuccess());
    }

    /**
     * 예약을 해제한다. execute가 예외로 실패해 완료 기록을 못 남긴 경우,
     * PENDING 레코드가 남아 재시도를 오염시키지 않도록 제거한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void remove(String idempotencyKey) {
        idempotencyRepository.deleteByIdempotencyKey(idempotencyKey);
    }
}
