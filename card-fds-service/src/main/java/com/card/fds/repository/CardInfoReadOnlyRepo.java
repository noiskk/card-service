package com.card.fds.repository;

import com.card.fds.entity.Card;
import org.springframework.data.repository.Repository;
import java.util.Optional;

/**
 * FDS 서비스용 읽기 전용 레포지토리
 * Spring Data의 최상위 Repository 인터페이스만 상속받아
 * 개발자가 명시한 메서드(조회)만 사용할 수 있도록 제한합니다.
 */
public interface CardInfoReadOnlyRepo extends Repository<Card, Long> {

    /**
     * 카드 번호로 카드 정보 단건 조회
     * Rule 2 (상태 검증)에 사용됩니다.
     */
    Optional<Card> findByCardNumber(String cardNumber);

}