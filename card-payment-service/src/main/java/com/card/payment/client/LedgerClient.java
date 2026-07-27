package com.card.payment.client;

import com.card.payment.dto.LedgerRecordRequest;
import com.card.payment.dto.LedgerRecordResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 원장 서비스 클라이언트.
 * 승인/거절 결과를 카드사 내부의 ledger-service에 기록한다.
 */
@FeignClient(name = "ledger-service", url = "${ledger.service.url}")
public interface LedgerClient {

    @PostMapping("/ledger/records")
    LedgerRecordResponse record(@RequestBody LedgerRecordRequest request);
}
