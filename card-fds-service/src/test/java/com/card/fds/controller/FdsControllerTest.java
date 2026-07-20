package com.card.fds.controller;

import com.card.fds.client.PaymentFeignClient;
import com.card.fds.dto.FdsRequestDto;
import com.card.fds.exception.CardNotFoundException;
import com.card.fds.exception.DuplicateTransactionException;
import com.card.fds.exception.GlobalExceptionHandler;
import com.card.fds.service.FdsInspectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FdsController가 예외를 직접 안 잡아도 GlobalExceptionHandler가 HTTP 응답을 조립하는지 검증.
 *
 * 핵심: FDS 차단(이상거래)이 HTTP 200 + 응답코드로 나가는지 -
 * 예전에는 403으로 응답해서 VAN의 Feign 클라이언트가 시스템 오류로 오인했다.
 * 그리고 payment 서비스가 진짜 죽었을 때만 5xx로 전파되는지도 확인.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FdsController - 예외처리 통합 테스트")
class FdsControllerTest {

    @Mock
    private FdsInspectionService fdsInspectionService;

    @Mock
    private PaymentFeignClient paymentFeignClient;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        FdsController controller = new FdsController(fdsInspectionService, paymentFeignClient);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String requestJson() throws Exception {
        FdsRequestDto req = FdsRequestDto.builder()
                .cardNum("4111111111111111")
                .amount(new BigDecimal("50000"))
                .merchantId("M1")
                .build();
        return objectMapper.writeValueAsString(req);
    }

    @Test
    @DisplayName("3초 중복 차단(BusinessException) -> HTTP 200 + responseCode 94")
    void duplicateBlocked_returns200WithCode94() throws Exception {
        when(fdsInspectionService.inspect(any())).thenThrow(new DuplicateTransactionException());

        mockMvc.perform(post("/api/fds/inspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value("94"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("카드 없음(BusinessException) -> HTTP 200 + responseCode 14")
    void cardNotFound_returns200WithCode14() throws Exception {
        when(fdsInspectionService.inspect(any())).thenThrow(new CardNotFoundException("4111111111111111"));

        mockMvc.perform(post("/api/fds/inspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value("14"));
    }

    @Test
    @DisplayName("결제 서비스 다운(FeignException) -> HTTP 503 (시스템 실패로 전파)")
    void paymentDown_returns503() throws Exception {
        when(fdsInspectionService.inspect(any())).thenReturn("DEBIT");
        when(paymentFeignClient.processPayment(any())).thenThrow(mock(FeignException.class));

        mockMvc.perform(post("/api/fds/inspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.responseCode").value("96"));
    }
}
