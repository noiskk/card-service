package com.card.fds.controller;

import com.card.fds.dto.FdsRequestDto;
import com.card.fds.exception.CardNotFoundException;
import com.card.fds.exception.DuplicateTransactionException;
import com.card.fds.exception.GlobalExceptionHandler;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FdsController가 예외를 직접 안 잡아도 GlobalExceptionHandler가 HTTP 응답을 조립하는지 검증.
 *
 * 핵심: FDS 차단(이상거래)이 HTTP 200 + 응답코드로 나가는지 —
 * 예전에는 403으로 응답해서 호출자의 Feign 클라이언트가 시스템 오류로 오인했다.
 * FDS는 이제 판정만 반환하는 leaf라 하위 서비스 호출이 없다.
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
        FdsController controller = new FdsController(fdsInspectionService);
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
    @DisplayName("정상 거래 -> HTTP 200 + 실제 카드 타입 반환")
    void passed_returns200WithCardType() throws Exception {
        when(fdsInspectionService.inspect(any())).thenReturn("CREDIT");

        mockMvc.perform(post("/api/fds/inspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responseCode").value("00"))
                // 요청에 실려온 값이 아니라 DB에서 확인한 타입을 돌려준다
                .andExpect(jsonPath("$.cardType").value("CREDIT"));
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
}
