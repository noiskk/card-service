package com.card.fds.controller;

import com.card.fds.dto.FdsRequestDto;
import com.card.fds.exception.CardNotFoundException;
import com.card.fds.exception.GlobalExceptionHandler;
import com.card.fds.exception.SuspiciousTransactionException;
import com.card.fds.rule.FdsDecision;
import com.card.fds.rule.FdsEvaluation;
import com.card.fds.service.FdsHistory;
import com.card.fds.service.FdsInspectionService;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FDS 판정 결과가 HTTP 응답으로 어떻게 나가는지 검증.
 *
 * 핵심: 차단이든 통과든 **HTTP 200 + 응답코드**로 나가야 한다.
 * 비2xx로 주면 호출자의 Feign이 예외로 처리해 "카드사 장애"로 오인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FdsController - 판정 응답 테스트")
class FdsControllerTest {

    @Mock
    private FdsInspectionService fdsInspectionService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        FdsController controller = new FdsController(fdsInspectionService, new FdsHistory());
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

    private FdsInspectionService.FdsInspectionResult result(FdsDecision decision, int score) {
        return new FdsInspectionService.FdsInspectionResult(
                "CREDIT", new FdsEvaluation(decision, score, List.of()));
    }

    @Test
    @DisplayName("정상 거래 -> 200 + 실제 카드 타입 + APPROVE")
    void approved() throws Exception {
        when(fdsInspectionService.inspect(any())).thenReturn(result(FdsDecision.APPROVE, 0));

        mockMvc.perform(post("/api/fds/inspect")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responseCode").value("00"))
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                // 요청에 실려온 값이 아니라 DB에서 확인한 타입을 돌려준다
                .andExpect(jsonPath("$.cardType").value("CREDIT"));
    }

    @Test
    @DisplayName("검토 대상(REVIEW) -> 승인하되 판정과 점수를 실어 보낸다")
    void review() throws Exception {
        when(fdsInspectionService.inspect(any())).thenReturn(result(FdsDecision.REVIEW, 45));

        mockMvc.perform(post("/api/fds/inspect")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isOk())
                // 차단이 아니므로 승인 흐름은 계속된다
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responseCode").value("00"))
                .andExpect(jsonPath("$.decision").value("REVIEW"))
                .andExpect(jsonPath("$.riskScore").value(45));
    }

    @Test
    @DisplayName("위험 점수 초과 차단 -> HTTP 200 + responseCode 94")
    void blocked() throws Exception {
        when(fdsInspectionService.inspect(any()))
                .thenThrow(new SuspiciousTransactionException(85, "VELOCITY_COUNT,MIDNIGHT_HIGH_AMOUNT",
                        "1분 내 4건 결제"));

        mockMvc.perform(post("/api/fds/inspect")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value("94"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("카드 없음 -> HTTP 200 + responseCode 14")
    void cardNotFound() throws Exception {
        when(fdsInspectionService.inspect(any())).thenThrow(new CardNotFoundException("4111111111111111"));

        mockMvc.perform(post("/api/fds/inspect")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value("14"));
    }
}
