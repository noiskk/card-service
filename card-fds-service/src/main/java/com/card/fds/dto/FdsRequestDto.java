package com.card.fds.dto;

import lombok.Getter;
import java.math.BigDecimal;

@Getter
public class FdsRequestDto {
    private String cardNum;
    private BigDecimal amount;
    private String merchantId;
    private String cardType;
}