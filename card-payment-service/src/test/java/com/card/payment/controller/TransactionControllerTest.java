package com.card.payment.controller;

import com.card.payment.dto.PaymentRequest;
import com.card.payment.exception.CardNotFoundException;
import com.card.payment.exception.DownstreamCallFailedException;
import com.card.payment.exception.GlobalExceptionHandler;
import com.card.payment.service.PaymentProcessorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TransactionController가 예외를 직접 안 잡아도(try-catch 제거됨)
 * GlobalExceptionHandler가 실제 HTTP 응답을 올바르게 조립하는지 검증한다.
 * (0단계 VAN PaymentGatewayControllerTest와 같은 패턴 - 실제 HTTP 계층까지 확인)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionController - 예외처리 통합 테스트")
class TransactionControllerTest {

    @Mock
    private PaymentProcessorService paymentProcessorService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        TransactionController controller = new TransactionController(paymentProcessorService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("카드 없음(BusinessException) -> HTTP 200 + responseCode 96")
    void cardNotFound_returns200WithCode96() throws Exception {
        PaymentRequest request = PaymentRequest.builder()
                .cardNum("0000").amount(10000L).merchantId("M1").cardType("DEBIT").build();

        when(paymentProcessorService.process(any()))
                .thenThrow(new CardNotFoundException("tx-1", "0000"));

        mockMvc.perform(post("/api/card/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value("96"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("은행 호출 실패(SystemException) -> HTTP 503")
    void downstreamCallFailed_returns503() throws Exception {
        PaymentRequest request = PaymentRequest.builder()
                .cardNum("1111").amount(10000L).merchantId("M1").cardType("DEBIT").build();

        when(paymentProcessorService.process(any()))
                .thenThrow(new DownstreamCallFailedException("tx-2", 10000L, new RuntimeException("timeout")));

        mockMvc.perform(post("/api/card/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.responseCode").value("96"));
    }

    @Test
    @DisplayName("예상 못한 오류(RuntimeException) -> HTTP 500 + 내부 메시지 노출 안 함")
    void unknownException_returns500WithGenericMessage() throws Exception {
        PaymentRequest request = PaymentRequest.builder()
                .cardNum("2222").amount(10000L).merchantId("M1").cardType("DEBIT").build();

        when(paymentProcessorService.process(any()))
                .thenThrow(new RuntimeException("내부 SQL 오류: SELECT * FROM cards WHERE ..."));

        mockMvc.perform(post("/api/card/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("시스템 오류가 발생했습니다"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SQL"))));
    }
}
