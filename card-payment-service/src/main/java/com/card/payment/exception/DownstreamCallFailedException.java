package com.card.payment.exception;

import org.springframework.http.HttpStatus;

/**
 * 하위 서비스 호출이 실패해 처리 결과를 확인할 수 없는 상태임을 나타내는 예외.
 *
 * 타임아웃/네트워크 오류 등으로 결과를 확인할 수 없는 경우이며, REJECTED로 단정해 기록하지 않고 전파한다.
 * 어느 서비스가 실패했는지 메시지에 남긴다 — 그러지 않으면 FDS 장애를 은행 장애로 오인하게 된다.
 * 은행 호출이 실패한 경우는 CompensationService가 대사 대상으로 남긴다.
 */
public class DownstreamCallFailedException extends SystemException {

    public DownstreamCallFailedException(String service, String transactionId, Long amount, Throwable cause) {
        super(service + " 호출 실패 - 처리 결과 확인 불가", cause, "96",
                HttpStatus.SERVICE_UNAVAILABLE, transactionId, amount);
    }
}
