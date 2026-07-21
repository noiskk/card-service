package com.card.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "결제 요청")
public class PaymentRequest {

    @Schema(description = "카드 번호", example = "4111111111111111", required = true)
    private String cardNum;

    @Schema(description = "결제 금액", example = "50000", required = true)
    private Long amount;

    @Schema(description = "가맹점 ID", example = "MERCHANT-001", required = true)
    private String merchantId;

    @Schema(description = "카드 타입 (CREDIT / DEBIT)", example = "DEBIT", required = true)
    private String cardType;

    @Schema(description = "멱등키 (원점 POS가 발급, 재시도해도 동일한 값)", example = "POS42-20260722-0001")
    private String idempotencyKey;
}