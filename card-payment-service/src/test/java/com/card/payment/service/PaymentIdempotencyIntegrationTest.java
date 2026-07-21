package com.card.payment.service;

import com.card.payment.client.BankClient;
import com.card.payment.dto.PaymentRequest;
import com.card.payment.dto.PaymentResponse;
import com.card.payment.dto.WithdrawResponse;
import com.card.payment.exception.DownstreamCallFailedException;
import com.card.payment.entity.Card;
import com.card.payment.entity.CardStatus;
import com.card.payment.entity.CardType;
import com.card.payment.entity.IdempotencyRecord;
import com.card.payment.entity.IdempotencyStatus;
import com.card.payment.repository.AuthorizationRepository;
import com.card.payment.repository.CardInfoRepository;
import com.card.payment.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결제 멱등성 통합 테스트.
 * 트랜잭션/프록시가 실제로 동작해야 검증되므로 단위 테스트가 아니라 @SpringBootTest + H2로.
 * 은행(Feign) 호출은 @MockBean으로 대체해 "출금이 실제로 몇 번 불렸는지"를 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("결제 멱등성 통합 테스트")
class PaymentIdempotencyIntegrationTest {

    @Autowired
    private PaymentProcessorService paymentProcessorService;
    @Autowired
    private CardInfoRepository cardInfoRepository;
    @Autowired
    private AuthorizationRepository authorizationRepository;
    @Autowired
    private IdempotencyRecordRepository idempotencyRepository;

    @MockBean
    private BankClient bankClient;

    @BeforeEach
    void setUp() {
        idempotencyRepository.deleteAll();
        authorizationRepository.deleteAll();
        cardInfoRepository.deleteAll();

        cardInfoRepository.save(Card.builder()
                .cardNumber("4111111111111111")
                .cardType(CardType.DEBIT)
                .cardStatus(CardStatus.ACTIVE)
                .perTransactionLimit(1_000_000L)
                .customerId(1L)
                .build());

        when(bankClient.withdraw(any())).thenReturn(withdrawSuccess());
    }

    @Test
    @DisplayName("같은 멱등키로 2번 요청 → 은행 출금 1번, 결과 동일, 원장 1건")
    void sameKey_withdrawsOnce() {
        PaymentRequest req = request("KEY-1");

        PaymentResponse first = paymentProcessorService.process(req);
        PaymentResponse second = paymentProcessorService.process(req);

        // 핵심: 재시도가 와도 은행 출금은 딱 1번
        verify(bankClient, times(1)).withdraw(any());

        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getResponseCode()).isEqualTo("00");
        // 재시도는 새로 처리하지 않고 원래 거래ID/결과를 그대로 반환
        assertThat(second.getTransactionId()).isEqualTo(first.getTransactionId());
        // 승인 원장에도 1건만 남음
        assertThat(authorizationRepository.count()).isEqualTo(1);
        // 멱등 레코드는 COMPLETED
        assertThat(idempotencyRepository.findByIdempotencyKey("KEY-1"))
                .get()
                .extracting(IdempotencyRecord::getStatus)
                .isEqualTo(IdempotencyStatus.COMPLETED);
    }

    @Test
    @DisplayName("다른 멱등키는 각각 처리 → 출금 2번, 원장 2건")
    void differentKeys_withdrawTwice() {
        paymentProcessorService.process(request("KEY-A"));
        paymentProcessorService.process(request("KEY-B"));

        verify(bankClient, times(2)).withdraw(any());
        assertThat(authorizationRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("execute 예외 시 예약 해제 → 같은 키 재시도가 정상 재시도된다")
    void executeThrows_reservationRemoved_retryReprocesses() {
        PaymentRequest req = request("KEY-FAIL");

        // 1. 은행이 죽어서 DownstreamCallFailedException → 예약은 해제되어야 함
        //    (이미 스터빙된 mock 재-스터빙이라 doThrow/doReturn 형식 사용)
        doThrow(new RuntimeException("은행 다운")).when(bankClient).withdraw(any());

        assertThatThrownBy(() -> paymentProcessorService.process(req))
                .isInstanceOf(DownstreamCallFailedException.class);

        // PENDING이 남지 않고 예약이 제거됨
        assertThat(idempotencyRepository.findByIdempotencyKey("KEY-FAIL")).isEmpty();

        // 2. 은행 복구 후 같은 키로 재시도 → 이번엔 정상 처리(오염된 PENDING 없음)
        doReturn(withdrawSuccess()).when(bankClient).withdraw(any());
        PaymentResponse retry = paymentProcessorService.process(req);

        assertThat(retry.isSuccess()).isTrue();
        verify(bankClient, times(2)).withdraw(any()); // 첫 시도(실패) + 재시도(성공)
        assertThat(authorizationRepository.count()).isEqualTo(1); // 성공 1건만
    }

    private PaymentRequest request(String key) {
        return PaymentRequest.builder()
                .cardNum("4111111111111111")
                .amount(50_000L)
                .merchantId("M1")
                .cardType("DEBIT")
                .idempotencyKey(key)
                .build();
    }

    private WithdrawResponse withdrawSuccess() {
        WithdrawResponse r = new WithdrawResponse();
        r.setSuccess(true);
        r.setResponseCode("00");
        r.setResponseMessage("출금 성공");
        return r;
    }
}
