package com.card.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * 카드사 내부 서비스 레지스트리.
 *
 * 카드사 안의 서비스(승인·FDS·원장·게이트웨이)만 여기 등록한다.
 * 은행·VAN은 다른 회사라 디스커버리 대상이 아니고 고정 엔드포인트로 연동한다.
 */
@EnableEurekaServer
@SpringBootApplication
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
