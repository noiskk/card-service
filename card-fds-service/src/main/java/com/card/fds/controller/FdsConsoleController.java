package com.card.fds.controller;

import com.card.fds.service.FdsHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * FDS 관제 화면 (시연용). 최근 판정 이력을 보여준다.
 */
@Controller
@RequiredArgsConstructor
public class FdsConsoleController {

    private final FdsHistory fdsHistory;

    @GetMapping("/")
    public String console(Model model) {
        var recent = fdsHistory.recent();
        model.addAttribute("entries", recent);
        model.addAttribute("blockedCount", recent.stream().filter(e -> !e.isPassed()).count());
        return "fds-console";
    }
}
