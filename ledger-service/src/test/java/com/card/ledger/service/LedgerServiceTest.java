package com.card.ledger.service;

import com.card.ledger.dto.LedgerRecordRequest;
import com.card.ledger.dto.LedgerRecordResponse;
import com.card.ledger.exception.LedgerNotFoundException;
import com.card.ledger.repository.AuthorizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 원장 기록 테스트.
 *
 * 승인 서비스는 네트워크 실패 시 같은 기록 요청을 재시도할 수 있다.
 * 그때 원장에 같은 거래가 두 번 쌓이지 않아야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("원장 기록 테스트")
class LedgerServiceTest {

    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private AuthorizationRepository authorizationRepository;

    @BeforeEach
    void setUp() {
        authorizationRepository.deleteAll();
    }

    private LedgerRecordRequest request(String txId, boolean success) {
        return LedgerRecordRequest.builder()
                .transactionId(txId)
                .cardNumber("4111111111111111")
                .amount(50_000L)
                .merchantId("M1")
                .responseCode(success ? "00" : "51")
                .success(success)
                .build();
    }

    @Test
    @DisplayName("승인 결과를 기록하면 승인 시각이 함께 남는다")
    void record_storesApprovalTime() {
        LedgerRecordResponse response = ledgerService.record(request("TX-1", true));

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getRecordedAt()).isNotNull();
        assertThat(authorizationRepository.findByTransactionId("TX-1")).get()
                .satisfies(a -> assertThat(a.getCreatedAt()).isNotNull());
    }

    @Test
    @DisplayName("거절도 원장에 남긴다")
    void record_storesRejection() {
        LedgerRecordResponse response = ledgerService.record(request("TX-2", false));

        assertThat(response.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("같은 거래ID로 두 번 기록해도 원장에는 한 건만 쌓인다")
    void record_isIdempotent() {
        LedgerRecordResponse first = ledgerService.record(request("TX-3", true));
        LedgerRecordResponse second = ledgerService.record(request("TX-3", true));

        assertThat(authorizationRepository.count()).isEqualTo(1);
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getRecordedAt()).isEqualTo(first.getRecordedAt());
    }

    @Test
    @DisplayName("없는 거래를 조회하면 예외")
    void find_notFound() {
        assertThatThrownBy(() -> ledgerService.findByTransactionId("NOPE"))
                .isInstanceOf(LedgerNotFoundException.class);
    }
}
