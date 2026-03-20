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
 * PaymentProcessorService 단위 테스트
 * 체크카드(은행 출금) 및 신용카드(한도 검사) 결제 로직을 테스트합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentProcessorService 테스트")
class PaymentProcessorServiceTest {

    @Mock
    private CardInfoRepository cardInfoRepository;

    @Mock
    private AuthorizationRepository authorizationRepository;

    @Mock
    private BankClient bankClient;

    @InjectMocks
    private PaymentProcessorService paymentProcessorService;

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

        // 체크카드 결제 요청
        debitPaymentRequest = PaymentRequest.builder()
                .cardNum("4111111111111111")
                .amount(50000L)
                .merchantId("MERCHANT-001")
                .cardType("DEBIT")
                .build();

        // 신용카드 결제 요청
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
        // Given
        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));
        when(bankClient.withdraw(any(WithdrawRequest.class)))
                .thenReturn(createWithdrawResponse(true));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PaymentResponse response = paymentProcessorService.process(debitPaymentRequest);

        // Then
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
        // Given: perTransactionLimit=1,000,000, 요청=1,500,000
        debitPaymentRequest.setAmount(1500000L);

        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PaymentResponse response = paymentProcessorService.process(debitPaymentRequest);

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("61");
        assertThat(response.getMessage()).contains("한도 초과");

        verify(bankClient, never()).withdraw(any(WithdrawRequest.class));
        verify(authorizationRepository).save(any(Authorization.class));
    }

    @Test
    @DisplayName("체크카드 결제 - 은행 출금 실패")
    void processDebit_WithdrawFailed() {
        // Given
        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));
        when(bankClient.withdraw(any(WithdrawRequest.class)))
                .thenReturn(createWithdrawResponse(false));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PaymentResponse response = paymentProcessorService.process(debitPaymentRequest);

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("51");
        assertThat(response.getMessage()).contains("출금 실패");

        verify(bankClient).withdraw(any(WithdrawRequest.class));
        verify(authorizationRepository).save(any(Authorization.class));
    }

    @Test
    @DisplayName("체크카드 결제 - 은행 서비스 예외 발생 시 시스템 오류")
    void processDebit_BankServiceException() {
        // Given
        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));
        when(bankClient.withdraw(any(WithdrawRequest.class)))
                .thenThrow(new RuntimeException("은행 서비스 연결 실패"));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PaymentResponse response = paymentProcessorService.process(debitPaymentRequest);

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("96");
        assertThat(response.getMessage()).contains("시스템 오류");

        verify(authorizationRepository).save(any(Authorization.class));
    }

    @Test
    @DisplayName("체크카드 결제 - 1회 한도와 정확히 같은 금액은 성공")
    void processDebit_ExactPerTransactionLimit() {
        // Given: perTransactionLimit=1,000,000, 요청=1,000,000
        debitPaymentRequest.setAmount(1000000L);

        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.of(debitCard));
        when(bankClient.withdraw(any(WithdrawRequest.class)))
                .thenReturn(createWithdrawResponse(true));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PaymentResponse response = paymentProcessorService.process(debitPaymentRequest);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResponseCode()).isEqualTo("00");

        verify(bankClient).withdraw(any(WithdrawRequest.class));
    }

    // ===== 신용카드 테스트 =====

    @Test
    @DisplayName("신용카드 결제 - 성공 + usedAmount 누적 확인")
    void processCredit_Success_UsedAmountUpdated() {
        // Given: perTransactionLimit=500,000, creditLimit=5,000,000, usedAmount=1,000,000
        // 요청=100,000 → 결제 후 usedAmount=1,100,000
        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));
        when(cardInfoRepository.save(any(Card.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PaymentResponse response = paymentProcessorService.process(creditPaymentRequest);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(response.getMessage()).isEqualTo("결제 성공");
        assertThat(creditCard.getUsedAmount()).isEqualTo(1100000L); // 1,000,000 + 100,000

        verify(cardInfoRepository).save(creditCard);
        verify(bankClient, never()).withdraw(any(WithdrawRequest.class));
        verify(authorizationRepository).save(any(Authorization.class));
    }

    @Test
    @DisplayName("신용카드 결제 - 1회 결제 한도 초과로 실패 (usedAmount 변경 없음)")
    void processCredit_PerTransactionLimitExceeded() {
        // Given: perTransactionLimit=500,000, 요청=600,000
        creditPaymentRequest.setAmount(600000L);

        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PaymentResponse response = paymentProcessorService.process(creditPaymentRequest);

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("61");
        assertThat(creditCard.getUsedAmount()).isEqualTo(1000000L); // 변경 없음

        verify(cardInfoRepository, never()).save(any(Card.class));
        verify(authorizationRepository).save(any(Authorization.class));
    }

    @Test
    @DisplayName("신용카드 결제 - 신용 잔여 한도 초과로 실패 (usedAmount 변경 없음)")
    void processCredit_CreditLimitExceeded() {
        // Given: creditLimit=5,000,000, usedAmount=1,000,000 → 잔여=4,000,000, 요청=4,500,000
        creditPaymentRequest.setAmount(4500000L);
        creditCard.setPerTransactionLimit(5000000L); // 1회 한도는 통과하도록

        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PaymentResponse response = paymentProcessorService.process(creditPaymentRequest);

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("51");
        assertThat(response.getMessage()).contains("신용 한도 초과");
        assertThat(creditCard.getUsedAmount()).isEqualTo(1000000L); // 변경 없음

        verify(cardInfoRepository, never()).save(any(Card.class));
        verify(authorizationRepository).save(any(Authorization.class));
    }

    @Test
    @DisplayName("신용카드 결제 - 잔여 한도와 정확히 같은 금액 성공")
    void processCredit_ExactRemainingLimit() {
        // Given: creditLimit=5,000,000, usedAmount=1,000,000 → 잔여=4,000,000, 요청=4,000,000
        creditPaymentRequest.setAmount(4000000L);
        creditCard.setPerTransactionLimit(5000000L); // 1회 한도는 통과하도록

        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));
        when(cardInfoRepository.save(any(Card.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PaymentResponse response = paymentProcessorService.process(creditPaymentRequest);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(creditCard.getUsedAmount()).isEqualTo(5000000L); // 1,000,000 + 4,000,000 = 한도 꽉 참

        verify(cardInfoRepository).save(creditCard);
    }

    @Test
    @DisplayName("신용카드 결제 - creditLimit이 null인 경우 실패")
    void processCredit_NullCreditLimit() {
        // Given
        creditCard.setCreditLimit(null);

        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PaymentResponse response = paymentProcessorService.process(creditPaymentRequest);

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponseCode()).isEqualTo("51");

        verify(cardInfoRepository, never()).save(any(Card.class));
    }

    @Test
    @DisplayName("신용카드 결제 - usedAmount가 null인 경우 0으로 처리되어 성공")
    void processCredit_NullUsedAmount() {
        // Given: usedAmount=null → 0으로 처리, 잔여한도=5,000,000
        creditCard.setUsedAmount(null);

        when(cardInfoRepository.findByCardNumber("6011111111111117"))
                .thenReturn(Optional.of(creditCard));
        when(cardInfoRepository.save(any(Card.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PaymentResponse response = paymentProcessorService.process(creditPaymentRequest);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(creditCard.getUsedAmount()).isEqualTo(100000L); // 0 + 100,000

        verify(cardInfoRepository).save(creditCard);
    }

    // ===== 공통 테스트 =====

    @Test
    @DisplayName("카드 조회 실패 - 존재하지 않는 카드번호")
    void process_CardNotFound() {
        // Given
        when(cardInfoRepository.findByCardNumber("4111111111111111"))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> paymentProcessorService.process(debitPaymentRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("카드 정보를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("승인 이력이 정상적으로 저장되는지 확인")
    void process_AuthorizationSaved() {
        // Given
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

        // When
        paymentProcessorService.process(creditPaymentRequest);

        // Then
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
