package com.card.fds.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * FDS 차단/오류 시 반환하는 응답 DTO.
 * VAN의 FdsInspectResponse가 읽을 수 있도록 success/responseCode/message 필드를 맞춘다.
 * (정상 통과 시에는 payment 서비스의 응답을 그대로 relay하므로 이 DTO를 쓰지 않는다.)
 */
@Getter
@Builder
public class FdsResponse {
    private boolean success;
    private String responseCode;
    private String message;
}
