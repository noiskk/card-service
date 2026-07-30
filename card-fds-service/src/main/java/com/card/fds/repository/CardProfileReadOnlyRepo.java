package com.card.fds.repository;

import com.card.fds.entity.CardProfile;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * 카드 프로파일 조회 전용.
 * 프로파일은 배치가 적재하는 데이터이므로 FDS는 읽기만 한다
 * (JpaRepository 대신 최상위 Repository를 상속해 쓰기 메서드를 아예 노출하지 않는다).
 */
public interface CardProfileReadOnlyRepo extends Repository<CardProfile, Long> {

    Optional<CardProfile> findByCardNumber(String cardNumber);
}
