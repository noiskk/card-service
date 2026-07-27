package com.card.payment.service;

import com.card.payment.client.BankClient;
import com.card.payment.client.FdsClient;
import com.card.payment.client.LedgerClient;
import com.card.payment.dto.FdsInspectRequest;
import com.card.payment.dto.FdsInspectResponse;
import com.card.payment.dto.LedgerRecordRequest;
import com.card.payment.dto.PaymentRequest;
import com.card.payment.dto.PaymentResponse;
import com.card.payment.dto.WithdrawRequest;
import com.card.payment.dto.WithdrawResponse;
import com.card.payment.entity.Card;
import com.card.payment.entity.CardStatus;
import com.card.payment.entity.CardType;
import com.card.payment.exception.CardNotFoundException;
import com.card.payment.exception.DownstreamCallFailedException;
import com.card.payment.exception.InvalidCardTypeException;
import com.card.payment.exception.LedgerRecordFailedException;
import com.card.payment.repository.CardInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PaymentExecutor 단위 테스트.
 * 체크카드(은행 출금)·신용카드(한도 검사) 결제 실행과, 원격 호출 실패 시 보상 연계를 검증한다.
 * (멱등성 조율은 PaymentProcessorService/통합 테스트가 담당)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentExecutor 테스트")
class PaymentExecutorTest {

    @Mock
    private CardInfoRepository cardInfoRepository;

    @Mock
    private FdsClient fdsClient;

    @Mock
    private BankClient bankClient;

    @Mock
    private LedgerClient ledgerClient;

    @Mock
    private CompensationService compensationService;

    @InjectMocks
    private PaymentExecutor paymentExecutor;

    private Card debitCard;
    private Card creditCard;
    private PaymentRequest debitPaymentRequest;
    private PaymentRequest creditPaymentRequest;

    @BeforeEach
    void setUp() {
        // FDS는 기본적으로 통과. cardType=null이면 실행기가 요청값을 그대로 쓴다.
        // FDS 차단/장애 테스트는 이 스텁을 덮어쓰므로 lenient()로 둔다(strict stubs 위반 방지).
        lenient().when(fdsClient.inspect(any(FdsInspectRequest.class))).thenReturn(fdsPass(null));

        debitCard = debitCard(1000000L);
        // 신용카드: 1회 한도 50만, 신용한도 500만, 사용액 100만 → 잔여 400만
        creditCard = creditCard(500000L, 5000000L, 1000000L);

        debitPaymentRequest = PaymentRequest.builder()
                .cardNum("4111111111111111")
                .amount(50000L)
                .merchantId("MERCHANT-001")
                .cardType("DEBIT")
                .build();

        creditPaymentRequest = PaymentRequest.builder()
                .cardNum("6011111111111117")
                .amount(100000L)
                .merchantId("MERCHANT-001")
                .cardType("CREDIT")
                .build();
    }

    private Card debitCard(Long perTransactionLimit) {
        return Card.builder()
                .id(1L)
                .cardNumber("4111111111111111")
                .cardType(CardType.DEBIT)
                .cardStatus(CardStatus.ACTIVE)
                .perTransactionLimit(perTransactionLimit)
                .customerId(1L)
                .build();
    }

    private Card creditCard(Long perTransactionLimit, Long creditLimit, Long usedAmount) {
        return Card.builder()
                .id(2L)
                .cardNumber("6011111111111117")
                .cardType(CardType.CREDIT)
                .cardStatus(CardStatus.ACTIVE)
                .perTransactionLimit(perTransactionLimit)
                .creditLimit(creditLimit)
                .usedAmount(usedAmount)
                .customerId(2L)
                .build();
    }

    // ===== 체크카드 =====

    @Test
    @DisplayName("체크카드 결제 - 성공")
    void processDebit_Success() {
        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));
        when(bankClient.withdraw(any(WithdrawRequest.class)))
                .thenReturn(createWithdrawResponse(true));

        PaymentResponse response = paymentExecutor.execute(debitPaymentRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(response.getMessage()).isEqualTo("결제 성공");
        assertThat(response.getAmount()).isEqualTo(50000L);
        assertThat(response.getTransactionId()).isNotNull();

        verify(bankClient).withdraw(any(WithdrawRequest.class));
        verify(ledgerClient).record(any(LedgerRecordRequest.class));
    }

    @Test
    @DisplayName("체크카드 결제 - 은행에 카드사 거래ID를 참조로 전달한다")
    void processDebit_PassesTransactionIdToBank() {
        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));
        when(bankClient.withdraw(any(WithdrawRequest.class)))
                .thenReturn(createWithdrawResponse(true));

        PaymentResponse response = paymentExecutor.execute(debitPaymentRequest);

        ArgumentCaptor<WithdrawRequest> captor = ArgumentCaptor.forClass(WithdrawRequest.class);
        verify(bankClient).withdraw(captor.capture());
        assertThat(captor.getValue().getTransactionId()).isEqualTo(response.getTransactionId());
    }

    @Test
    @DisplayName("체크카드 결제 - 1회 결제 한도 초과로 실패 (은행 호출 안함)")
    void processDebit_PerTransactionLimitExceeded() {
        debitPaymentRequest.setAmount(1500000L);

        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));

        PaymentResponse response = paymentExecutor.execute(debitPaymentRequest);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("61");
        assertThat(response.getMessage()).contains("한도 초과");

        verify(bankClient, never()).withdraw(any(WithdrawRequest.class));
        verify(ledgerClient).record(any(LedgerRecordRequest.class));
    }

    @Test
    @DisplayName("체크카드 결제 - 은행 출금 실패")
    void processDebit_WithdrawFailed() {
        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));
        when(bankClient.withdraw(any(WithdrawRequest.class)))
                .thenReturn(createWithdrawResponse(false));

        PaymentResponse response = paymentExecutor.execute(debitPaymentRequest);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("51");
        assertThat(response.getMessage()).contains("출금 실패");

        verify(ledgerClient).record(any(LedgerRecordRequest.class));
    }

    @Test
    @DisplayName("체크카드 결제 - 은행 호출 실패 시 불확실 거래로 남기고 예외 전파 (원장 기록 안 함)")
    void processDebit_BankCallFailed_RecordsUncertain() {
        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));
        when(bankClient.withdraw(any(WithdrawRequest.class)))
                .thenThrow(new RuntimeException("은행 서비스 연결 실패"));

        assertThatThrownBy(() -> paymentExecutor.execute(debitPaymentRequest))
                .isInstanceOf(DownstreamCallFailedException.class);

        // 출금 여부를 모르므로 취소를 보내지 않고 대사 대상으로만 남긴다
        verify(compensationService).recordUncertainWithdrawal(anyString(), eq(debitPaymentRequest));
        verify(compensationService, never()).compensateWithdrawal(anyString(), any(), any());
        verify(ledgerClient, never()).record(any(LedgerRecordRequest.class));
    }

    @Test
    @DisplayName("체크카드 결제 - 출금 성공 후 원장 기록 실패 시 보상(취소) 수행")
    void processDebit_LedgerFailedAfterWithdraw_Compensates() {
        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));
        when(bankClient.withdraw(any(WithdrawRequest.class)))
                .thenReturn(createWithdrawResponse(true));
        when(ledgerClient.record(any(LedgerRecordRequest.class)))
                .thenThrow(new RuntimeException("ledger-service 응답 없음"));

        assertThatThrownBy(() -> paymentExecutor.execute(debitPaymentRequest))
                .isInstanceOf(LedgerRecordFailedException.class);

        verify(compensationService).compensateWithdrawal(anyString(), eq(debitPaymentRequest), any());
    }

    @Test
    @DisplayName("체크카드 결제 - 1회 한도와 정확히 같은 금액은 성공")
    void processDebit_ExactPerTransactionLimit() {
        debitPaymentRequest.setAmount(1000000L);

        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));
        when(bankClient.withdraw(any(WithdrawRequest.class)))
                .thenReturn(createWithdrawResponse(true));

        PaymentResponse response = paymentExecutor.execute(debitPaymentRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResponseCode()).isEqualTo("00");
        verify(bankClient).withdraw(any(WithdrawRequest.class));
    }

    // ===== 신용카드 =====

    @Test
    @DisplayName("신용카드 결제 - 성공 + usedAmount 누적 확인")
    void processCredit_Success_UsedAmountUpdated() {
        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));

        PaymentResponse response = paymentExecutor.execute(creditPaymentRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResponseCode()).isEqualTo("00");
        // 영속 엔티티라 더티 체킹으로 반영된다(명시적 save 없음)
        assertThat(creditCard.getUsedAmount()).isEqualTo(1100000L);

        verify(bankClient, never()).withdraw(any(WithdrawRequest.class));
        verify(ledgerClient).record(any(LedgerRecordRequest.class));
    }

    @Test
    @DisplayName("신용카드 결제 - 1회 결제 한도 초과로 실패 (usedAmount 변경 없음)")
    void processCredit_PerTransactionLimitExceeded() {
        creditPaymentRequest.setAmount(600000L);

        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));

        PaymentResponse response = paymentExecutor.execute(creditPaymentRequest);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("61");
        assertThat(creditCard.getUsedAmount()).isEqualTo(1000000L);
        verify(ledgerClient).record(any(LedgerRecordRequest.class));
    }

    @Test
    @DisplayName("신용카드 결제 - 신용 잔여 한도 초과로 실패 (usedAmount 변경 없음)")
    void processCredit_CreditLimitExceeded() {
        creditPaymentRequest.setAmount(4500000L);
        creditCard = creditCard(5000000L, 5000000L, 1000000L);

        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));

        PaymentResponse response = paymentExecutor.execute(creditPaymentRequest);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("51");
        assertThat(response.getMessage()).contains("신용 한도 초과");
        assertThat(creditCard.getUsedAmount()).isEqualTo(1000000L);
    }

    @Test
    @DisplayName("신용카드 결제 - 잔여 한도와 정확히 같은 금액 성공")
    void processCredit_ExactRemainingLimit() {
        creditPaymentRequest.setAmount(4000000L);
        creditCard = creditCard(5000000L, 5000000L, 1000000L);

        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));

        PaymentResponse response = paymentExecutor.execute(creditPaymentRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(creditCard.getUsedAmount()).isEqualTo(5000000L);
    }

    @Test
    @DisplayName("신용카드 결제 - creditLimit이 null인 경우 실패")
    void processCredit_NullCreditLimit() {
        creditCard = creditCard(500000L, null, 1000000L);

        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));

        PaymentResponse response = paymentExecutor.execute(creditPaymentRequest);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("51");
    }

    @Test
    @DisplayName("신용카드 결제 - usedAmount가 null인 경우 0으로 처리되어 성공")
    void processCredit_NullUsedAmount() {
        creditCard = creditCard(500000L, 5000000L, null);

        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));

        PaymentResponse response = paymentExecutor.execute(creditPaymentRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(creditCard.getUsedAmount()).isEqualTo(100000L);
    }

    // ===== 공통 =====

    @Test
    @DisplayName("카드 조회 실패 - 존재하지 않는 카드번호")
    void process_CardNotFound() {
        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentExecutor.execute(debitPaymentRequest))
                .isInstanceOf(CardNotFoundException.class)
                .hasMessageContaining("카드 정보를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("카드 타입이 DEBIT/CREDIT이 아니면 InvalidCardTypeException")
    void process_InvalidCardType() {
        debitPaymentRequest.setCardType("BITCOIN");
        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));

        assertThatThrownBy(() -> paymentExecutor.execute(debitPaymentRequest))
                .isInstanceOf(InvalidCardTypeException.class)
                .hasMessageContaining("BITCOIN");
    }

    @Test
    @DisplayName("원장 기록 요청에 승인 정보가 정확히 담긴다")
    void process_LedgerRecordContents() {
        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));

        PaymentResponse response = paymentExecutor.execute(creditPaymentRequest);

        ArgumentCaptor<LedgerRecordRequest> captor = ArgumentCaptor.forClass(LedgerRecordRequest.class);
        verify(ledgerClient).record(captor.capture());

        LedgerRecordRequest recorded = captor.getValue();
        assertThat(recorded.getTransactionId()).isEqualTo(response.getTransactionId());
        assertThat(recorded.getCardNumber()).isEqualTo("6011111111111117");
        assertThat(recorded.getAmount()).isEqualTo(100000L);
        assertThat(recorded.getMerchantId()).isEqualTo("MERCHANT-001");
        assertThat(recorded.getResponseCode()).isEqualTo("00");
        assertThat(recorded.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("FDS가 차단하면 출금 없이 거절 원장만 남긴다")
    void fdsBlocked_rejectsWithoutWithdraw() {
        when(fdsClient.inspect(any(FdsInspectRequest.class))).thenReturn(
                FdsInspectResponse.builder()
                        .success(false).responseCode("94").message("중복 거래 의심").build());

        PaymentResponse response = paymentExecutor.execute(debitPaymentRequest);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("94");

        verify(cardInfoRepository, never()).findByCardNumber(anyString());
        verify(bankClient, never()).withdraw(any(WithdrawRequest.class));
        verify(ledgerClient).record(any(LedgerRecordRequest.class));
    }

    @Test
    @DisplayName("FDS가 알려준 카드 타입이 요청값보다 우선한다")
    void fdsCardTypeOverridesRequest() {
        // 요청은 DEBIT라고 주장하지만 실제로는 CREDIT 카드
        debitPaymentRequest.setCardNum("6011111111111117");
        when(fdsClient.inspect(any(FdsInspectRequest.class))).thenReturn(fdsPass("CREDIT"));
        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));

        PaymentResponse response = paymentExecutor.execute(debitPaymentRequest);

        assertThat(response.isSuccess()).isTrue();
        // 신용카드로 처리됐으므로 은행 출금은 일어나지 않는다
        verify(bankClient, never()).withdraw(any(WithdrawRequest.class));
        assertThat(creditCard.getUsedAmount()).isEqualTo(1050000L);
    }

    @Test
    @DisplayName("FDS 호출이 실패하면 승인 불가로 전파하고 아무것도 기록하지 않는다")
    void fdsCallFailed_propagates() {
        when(fdsClient.inspect(any(FdsInspectRequest.class)))
                .thenThrow(new RuntimeException("FDS 응답 없음"));

        assertThatThrownBy(() -> paymentExecutor.execute(debitPaymentRequest))
                .isInstanceOf(DownstreamCallFailedException.class);

        verify(bankClient, never()).withdraw(any(WithdrawRequest.class));
        verify(ledgerClient, never()).record(any(LedgerRecordRequest.class));
        verifyNoInteractions(compensationService);
    }

    private FdsInspectResponse fdsPass(String cardType) {
        return FdsInspectResponse.builder()
                .success(true).responseCode("00").message("정상 거래").cardType(cardType).build();
    }

    private WithdrawResponse createWithdrawResponse(boolean success) {
        WithdrawResponse response = new WithdrawResponse();
        response.setSuccess(success);
        response.setResponseCode(success ? "00" : "51");
        response.setResponseMessage(success ? "출금 성공" : "잔액 부족");
        return response;
    }
}
