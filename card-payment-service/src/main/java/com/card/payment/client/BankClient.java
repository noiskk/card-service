package com.card.payment.client;

import com.card.payment.dto.WithdrawRequest;
import com.card.payment.dto.WithdrawResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 은행 서비스 클라이언트 인터페이스
 */
@FeignClient(name = "bank-service", url = "${bank.service.url}")
public interface BankClient {

    @PostMapping("/api/bank/accounts/withdraw")
    WithdrawResponse withdraw(@RequestBody WithdrawRequest request);}
