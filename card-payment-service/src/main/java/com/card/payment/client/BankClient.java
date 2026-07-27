package com.card.payment.client;

import com.card.payment.dto.CancelRequest;
import com.card.payment.dto.CancelResponse;
import com.card.payment.dto.WithdrawRequest;
import com.card.payment.dto.WithdrawResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 은행 서비스 클라이언트 인터페이스.
 * 은행은 카드사 외부 회사이므로 서비스 디스커버리 대상이 아니라 고정 엔드포인트로 연동한다.
 */
@FeignClient(name = "bank-service", url = "${bank.service.url}")
public interface BankClient {

    @PostMapping("/api/bank/accounts/withdraw")
    WithdrawResponse withdraw(@RequestBody WithdrawRequest request);

    /** 출금 취소(망취소·보상). 같은 거래ID로 여러 번 호출해도 한 번만 반영된다. */
    @PostMapping("/api/bank/accounts/cancel")
    CancelResponse cancel(@RequestBody CancelRequest request);
}
