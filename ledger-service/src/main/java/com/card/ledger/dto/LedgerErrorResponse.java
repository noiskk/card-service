package com.card.ledger.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LedgerErrorResponse {
    private boolean success;
    private String errorCode;
    private String message;
}
