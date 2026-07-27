package com.card.ledger.service;

import com.card.ledger.dto.LedgerRecordRequest;
import com.card.ledger.dto.LedgerRecordResponse;
import com.card.ledger.entity.Authorization;
import com.card.ledger.entity.AuthorizationStatus;
import com.card.ledger.exception.LedgerNotFoundException;
import com.card.ledger.repository.AuthorizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private final AuthorizationRepository repository;

    /**
     * 승인 결과를 원장에 기록한다.
     * 같은 transactionId가 이미 있으면 기존 원장을 반환한다(중복 INSERT 방지 = 기록 멱등성).
     */
    @Transactional
    public LedgerRecordResponse record(LedgerRecordRequest req) {
        Optional<Authorization> found = repository.findByTransactionId(req.getTransactionId());

        if(found.isPresent()){
            log.debug("이미 기록된 원장 반환 - txId: {}", req.getTransactionId());
            return toResponse(found.get());
        }
        Authorization saved = repository.save(Authorization.builder()
                .transactionId(req.getTransactionId())
                .cardNumber(req.getCardNumber())
                .amount(req.getAmount())
                .merchantId(req.getMerchantId())
                .responseCode(req.getResponseCode())
                .status(req.isSuccess() ? AuthorizationStatus.APPROVED : AuthorizationStatus.REJECTED)
                .build());

        log.info("원장 기록 - txId: {}, status: {}", saved.getTransactionId(), saved.getStatus());
        return toResponse(saved);

    }

    @Transactional(readOnly = true)
    public LedgerRecordResponse findByTransactionId(String transactionId) {
        return repository.findByTransactionId(transactionId)
                .map(existing -> toResponse(existing))
                .orElseThrow(() -> new LedgerNotFoundException(transactionId));
    }

    private LedgerRecordResponse toResponse(Authorization a) {
        return LedgerRecordResponse.builder()
                .id(a.getId())
                .transactionId(a.getTransactionId())
                .status(a.getStatus().name())
                .recordedAt(a.getCreatedAt())
                .build();
    }
}
