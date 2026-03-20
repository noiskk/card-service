package com.card.fds.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 헬스체크 컨트롤러
 */
@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "서비스 상태 확인 API")
public class HealthController {
    
    @Operation(
        summary = "서비스 상태 확인",
        description = "Card Authorization Service의 상태를 확인합니다."
    )
    @GetMapping
    public Map<String, String> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Card Authorization Service");
        return response;
    }
}
