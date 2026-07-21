package com.card.payment.service;

import com.card.payment.client.BankClient;
import com.card.payment.dto.PaymentRequest;
import com.card.payment.dto.PaymentResponse;
import com.card.payment.dto.WithdrawRequest;
import com.card.payment.dto.WithdrawResponse;
import com.card.payment.entity.Authorization;
import com.card.payment.entity.Card;
import com.card.payment.entity.CardStatus;
import com.card.payment.entity.CardType;
import com.card.payment.exception.CardNotFoundException;
import com.card.payment.exception.DownstreamCallFailedException;
import com.card.payment.exception.InvalidCardTypeException;
import com.card.payment.repository.AuthorizationRepository;
import com.card.payment.repository.CardInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PaymentExecutor 단위 테스트
 * 체크카드(은행 출금) 및 신용카드(한도 검사) 결제 실행 로직을 테스트합니다.
 * (멱등성 조율은 PaymentProcessorService/통합 테스트가 담당)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentExecutor 테스트")
class PaymentExecutorTest {

    @Mock
    private CardInfoRepository cardInfoRepository;

    @Mock
    private AuthorizationRepository authorizationRepository;

    @Mock
    private BankClient bankClient;

    @InjectMocks
    private PaymentExecutor paymentExecutor;

    private Card debitCard;
    private Card creditCard;
    private PaymentRequest debitPaymentRequest;
    private PaymentRequest creditPaymentRequest;

    @BeforeEach
    void setUp() {
        // 체크카드 (1회 결제 한도: 1,000,000원)
        debitCard = Card.builder()
                .id(1L)
                .cardNumber("4111111111111111")
                .cardType(CardType.DEBIT)
                .cardStatus(CardStatus.ACTIVE)
                .perTransactionLimit(1000000L)
                .customerId(1L)
                .build();

        // 신용카드 (1회 결제 한도: 500,000원, 신용한도: 5,000,000원, 사용액: 1,000,000원 → 잔여: 4,000,000원)
        creditCard = Card.builder()
                .id(2L)
                .cardNumber("6011111111111117")
                .cardType(CardType.CREDIT)
                .cardStatus(CardStatus.ACTIVE)
                .perTransactionLimit(500000L)
                .creditLimit(5000000L)
                .usedAmount(1000000L)
                .customerId(2L)
                .build();

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

    // ===== 체크카드 테스트 =====

    @Test
    @DisplayName("체크카드 결제 - 성공")
    void processDebit_Success() {
        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));
        when(bankClient.withdraw(any(WithdrawRequest.class)))
                .thenReturn(createWithdrawResponse(true));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentExecutor.execute(debitPaymentRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(response.getMessage()).isEqualTo("결제 성공");
        assertThat(response.getAmount()).isEqualTo(50000L);
        assertThat(response.getTransactionId()).isNotNull();

        verify(bankClient).withdraw(any(WithdrawRequest.class));
        verify(authorizationRepository).save(any(Authorization.class));
    }

    @Test
    @DisplayName("체크카드 결제 - 1회 결제 한도 초과로 실패 (은행 호출 안함)")
    void processDebit_PerTransactionLimitExceeded() {
        debitPaymentRequest.setAmount(1500000L);

        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentExecutor.execute(debitPaymentRequest);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("61");
        assertThat(response.getMessage()).contains("한도 초과");

        verify(bankClient, never()).withdraw(any(WithdrawRequest.class));
        verify(authorizationRepository).save(any(Authorization.class));
    }

    @Test
    @DisplayName("체크카드 결제 - 은행 출금 실패")
    void processDebit_WithdrawFailed() {
        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));
        when(bankClient.withdraw(any(WithdrawRequest.class)))
                .thenReturn(createWithdrawResponse(false));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentExecutor.execute(debitPaymentRequest);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("51");
        assertThat(response.getMessage()).contains("출금 실패");

        verify(bankClient).withdraw(any(WithdrawRequest.class));
        verify(authorizationRepository).save(any(Authorization.class));
    }

    @Test
    @DisplayName("체크카드 결제 - 은행 서비스 예외 발생 시 DownstreamCallFailedException 전파 (Authorization 저장 안 함)")
    void processDebit_BankServiceException() {
        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));
        when(bankClient.withdraw(any(WithdrawRequest.class)))
                .thenThrow(new RuntimeException("은행 서비스 연결 실패"));

        assertThatThrownBy(() -> paymentExecutor.execute(debitPaymentRequest))
                .isInstanceOf(DownstreamCallFailedException.class);

        verify(authorizationRepository, never()).save(any(Authorization.class));
    }

    @Test
    @DisplayName("체크카드 결제 - 1회 한도와 정확히 같은 금액은 성공")
    void processDebit_ExactPerTransactionLimit() {
        debitPaymentRequest.setAmount(1000000L);

        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));
        when(bankClient.withdraw(any(WithdrawRequest.class)))
                .thenReturn(createWithdrawResponse(true));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentExecutor.execute(debitPaymentRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResponseCode()).isEqualTo("00");

        verify(bankClient).withdraw(any(WithdrawRequest.class));
    }

    // ===== 신용카드 테스트 =====

    @Test
    @DisplayName("신용카드 결제 - 성공 + usedAmount 누적 확인")
    void processCredit_Success_UsedAmountUpdated() {
        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));
        when(cardInfoRepository.save(any(Card.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentExecutor.execute(creditPaymentRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(response.getMessage()).isEqualTo("결제 성공");
        assertThat(creditCard.getUsedAmount()).isEqualTo(1100000L);

        verify(cardInfoRepository).save(creditCard);
        verify(bankClient, never()).withdraw(any(WithdrawRequest.class));
        verify(authorizationRepository).save(any(Authorization.class));
    }

    @Test
    @DisplayName("신용카드 결제 - 1회 결제 한도 초과로 실패 (usedAmount 변경 없음)")
    void processCredit_PerTransactionLimitExceeded() {
        creditPaymentRequest.setAmount(600000L);

        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentExecutor.execute(creditPaymentRequest);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("61");
        assertThat(creditCard.getUsedAmount()).isEqualTo(1000000L);

        verify(cardInfoRepository, never()).save(any(Card.class));
        verify(authorizationRepository).save(any(Authorization.class));
    }

    @Test
    @DisplayName("신용카드 결제 - 신용 잔여 한도 초과로 실패 (usedAmount 변경 없음)")
    void processCredit_CreditLimitExceeded() {
        creditPaymentRequest.setAmount(4500000L);
        creditCard.setPerTransactionLimit(5000000L);

        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentExecutor.execute(creditPaymentRequest);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("51");
        assertThat(response.getMessage()).contains("신용 한도 초과");
        assertThat(creditCard.getUsedAmount()).isEqualTo(1000000L);

        verify(cardInfoRepository, never()).save(any(Card.class));
        verify(authorizationRepository).save(any(Authorization.class));
    }

    @Test
    @DisplayName("신용카드 결제 - 잔여 한도와 정확히 같은 금액 성공")
    void processCredit_ExactRemainingLimit() {
        creditPaymentRequest.setAmount(4000000L);
        creditCard.setPerTransactionLimit(5000000L);

        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));
        when(cardInfoRepository.save(any(Card.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentExecutor.execute(creditPaymentRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(creditCard.getUsedAmount()).isEqualTo(5000000L);

        verify(cardInfoRepository).save(creditCard);
    }

    @Test
    @DisplayName("신용카드 결제 - creditLimit이 null인 경우 실패")
    void processCredit_NullCreditLimit() {
        creditCard.setCreditLimit(null);

        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentExecutor.execute(creditPaymentRequest);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("51");

        verify(cardInfoRepository, never()).save(any(Card.class));
    }

    @Test
    @DisplayName("신용카드 결제 - usedAmount가 null인 경우 0으로 처리되어 성공")
    void processCredit_NullUsedAmount() {
        creditCard.setUsedAmount(null);

        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));
        when(cardInfoRepository.save(any(Card.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentExecutor.execute(creditPaymentRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(creditCard.getUsedAmount()).isEqualTo(100000L);

        verify(cardInfoRepository).save(creditCard);
    }

    // ===== 공통 테스트 =====

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
    @DisplayName("승인 이력이 정상적으로 저장되는지 확인")
    void process_AuthorizationSaved() {
        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));
        when(cardInfoRepository.save(any(Card.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> {
                    Authorization saved = invocation.getArgument(0);
                    assertThat(saved.getCard()).isEqualTo(creditCard);
                    assertThat(saved.getAmount()).isEqualTo(100000L);
                    assertThat(saved.getMerchantId()).isEqualTo("MERCHANT-001");
                    assertThat(saved.getTransactionId()).isNotNull();
                    assertThat(saved.getResponseCode()).isEqualTo("00");
                    return saved;
                });

        paymentExecutor.execute(creditPaymentRequest);

        verify(authorizationRepository).save(any(Authorization.class));
    }

    private WithdrawResponse createWithdrawResponse(boolean success) {
        WithdrawResponse response = new WithdrawResponse();
        response.setSuccess(success);
        response.setResponseCode(success ? "00" : "51");
        response.setResponseMessage(success ? "출금 성공" : "잔액 부족");
        return response;
    }
}
