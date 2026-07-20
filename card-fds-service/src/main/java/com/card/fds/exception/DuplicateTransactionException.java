package com.card.fds.exception;

import org.springframework.http.HttpStatus;

/**
 * 단기 다발성 거래(3초 이내 동일 카드 재요청)로 차단됨을 나타내는 예외.
 *
 * FDS의 이상거래 차단은 정상적인 비즈니스 결과이므로 HTTP 200으로 응답한다.
 * (403으로 내리면 이를 호출하는 VAN의 Feign 클라이언트가 시스템 오류로 오인함)
 * 응답 코드 94 = 중복거래.
 */
public class DuplicateTransactionException extends BusinessException {
    public DuplicateTransactionException() {
        super("단기 다발성 거래로 차단되었습니다.", "94", HttpStatus.OK);
    }
}
