package com.card.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger 설정
 * Card Authorization Service의 API 문서를 자동 생성합니다.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cardPaymentOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Card Payment Service API")
                        .description("카드 결제 서비스 API 문서\n\n" +
                                "## 주요 기능\n" +
                                "- 체크카드 결제 (은행 출금 요청)\n" +
                                "- 신용카드 결제 (1회 결제 한도 검사)\n" +
                                "- 결제 이력 저장\n\n" +
                                "## 응답 코드\n" +
                                "- `00`: 결제 성공\n" +
                                "- `51`: 잔액 부족 / 출금 실패\n" +
                                "- `61`: 1회 결제 한도 초과\n" +
                                "- `96`: 시스템 오류")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Card Payment System")
                                .email("support@cardpayment.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:9091")
                                .description("로컬 개발 서버")
                ));
    }
}