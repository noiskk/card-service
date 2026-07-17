package com.card.payment.exception;

import org.springframework.http.HttpStatus;

/**
 * 은행 서비스 호출이 실패했고, 실제로 출금이 성공했는지 알 수 없는 상태임을 나타내는 예외.
 *
 * 타임아웃/네트워크 오류 등으로 은행 쪽 처리 결과를 확인할 수 없는 경우이며,
 * REJECTED로 단정해 기록하지 않고 예외로 전파한다.
 * TODO(3단계 Saga): 이 예외가 발생하면 은행 측 상태를 조회하거나 보상 트랜잭션으로
 * 확실히 정리해야 한다.
 */

public class DownstreamCallFailedException extends SystemException{
    public DownstreamCallFailedException(String transactionId, Long amount, Throwable cause) {
        super("은행 서비스 호출 실패 - 출금 성공 여부 확인 불가", cause, "96", HttpStatus.SERVICE_UNAVAILABLE, transactionId, amount);
    }
}
