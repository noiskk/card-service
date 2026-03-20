package com.card.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "결제 응답")
public class PaymentResponse {

    @Schema(description = "거래 고유 번호 (UUID)", example =
            "550e8400-e29b-41d4-a716-446655440000")
    private String transactionId;

    @Schema(description = "결과 코드", example = "SUCCESS")
    private String responseCode;

    @Schema(description = "응답 메시지", example = "결제 성공")
    private String message;

    @Schema(description = "결제 금액", example = "50000")
    private Long amount;

    @Schema(description = "처리 일시")
    private LocalDateTime processedAt;

    @Schema(description = "성공 여부", example = "true")
    private boolean success;
}