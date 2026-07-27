package com.card.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 카드사 단일 진입점.
 *
 * 외부(VAN)는 카드사 내부 서비스 주소를 알 필요 없이 이 게이트웨이만 알면 된다.
 * 내부 서비스 위치는 Eureka에서 이름으로 찾아 라우팅한다.
 */
@EnableDiscoveryClient
@SpringBootApplication
public class CardGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(CardGatewayApplication.class, args);
    }
}
