package com.card.payment.repository;

import com.card.payment.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 카드 리포지토리
 * 카드 정보에 대한 데이터베이스 접근을 담당합니다.
 */
@Repository
public interface CardInfoRepository extends JpaRepository<Card, Long> {

    /**
     * 카드 번호로 카드 조회
     * @param cardNumber 카드 번호
     * @return 카드 정보
     */
    Optional<Card> findByCardNumber(String cardNumber);
}
