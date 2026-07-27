package com.card.payment.client;

import com.card.payment.dto.FdsInspectRequest;
import com.card.payment.dto.FdsInspectResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 이상거래 탐지 서비스 클라이언트 (카드사 내부).
 * url 없이 서비스 이름만 지정한다 — 실제 주소는 Eureka에서 찾는다.
 */
@FeignClient(name = "card-fds-service")
public interface FdsClient {

    @PostMapping("/api/fds/inspect")
    FdsInspectResponse inspect(@RequestBody FdsInspectRequest request);
}
