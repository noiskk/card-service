package com.card.fds.config;

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
    public OpenAPI cardAuthorizationOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Card Authorization Service API")
                        .description("카드 승인 서비스 API 문서\n\n" +
                                "## 주요 기능\n" +
                                "- 카드 유효성 검증 (Luhn 알고리즘, 카드 상태, 유효기간, PIN)\n" +
                                "- 체크카드 승인 (은행 잔액 조회 및 출금)\n" +
                                "- 신용카드 승인 (신용 한도 확인 및 차감)\n" +
                                "- 승인/거절 내역 저장\n\n" +
                                "## 응답 코드\n" +
                                "- `00`: 승인\n" +
                                "- `14`: 카드 정지/분실/해지\n" +
                                "- `51`: 잔액 부족\n" +
                                "- `54`: 유효기간 만료\n" +
                                "- `55`: PIN 오류\n" +
                                "- `61`: 한도 초과\n" +
                                "- `96`: 시스템 오류\n\n" +
                                "## 테스트 카드\n" +
                                "아래 예제 섹션에서 다양한 시나리오를 테스트할 수 있습니다.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Card Payment System")
                                .email("support@cardpayment.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:9090")
                                .description("로컬 개발 서버")
                ));
    }
}
