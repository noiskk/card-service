package com.card.fds.controller;

import com.card.fds.service.FdsHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class FdsConsoleController {

    private final FdsHistory fdsHistory;

    @GetMapping("/")
    public String console(Model model) {
        List<FdsHistory.Entry> recent = fdsHistory.recent();
        long blocked = recent.stream().filter(e -> !e.isPassed()).count();

        model.addAttribute("entries", recent);
        model.addAttribute("total", recent.size());
        model.addAttribute("passed", recent.size() - blocked);
        model.addAttribute("blocked", blocked);
        model.addAttribute("blockRate", recent.isEmpty() ? "—"
                : String.format("%.1f%%", blocked * 100.0 / recent.size()));
        model.addAttribute("dupBlocked", recent.stream().filter(e -> "94".equals(e.getResponseCode())).count());
        model.addAttribute("cardBlocked", recent.stream().filter(e -> "14".equals(e.getResponseCode())).count());
        return "fds-console";
    }
}
