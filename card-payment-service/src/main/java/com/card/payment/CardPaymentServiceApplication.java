package com.card.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 카드 결제 승인 서비스 애플리케이션
 */
@SpringBootApplication
@EnableFeignClients
@EnableJpaAuditing
public class CardPaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(com.card.payment.CardPaymentServiceApplication.class, args);
    }
}
