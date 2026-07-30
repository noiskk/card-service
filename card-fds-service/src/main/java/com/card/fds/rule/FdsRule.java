package com.card.fds.rule;

/**
 * 탐지 룰 하나.
 *
 * 룰을 인터페이스로 둔 이유: 룰을 추가·제거할 때 엔진을 고치지 않아도 되고
 * (스프링이 구현체를 모두 주입해준다), 룰마다 독립적으로 단위 테스트할 수 있다.
 * 실무에서 룰은 자주 바뀌므로 이 지점의 확장성이 중요하다.
 */
public interface FdsRule {

    /** 룰 식별자 (응답·이력에 남는 이름) */
    String name();

    RuleResult evaluate(FdsContext context);
}
