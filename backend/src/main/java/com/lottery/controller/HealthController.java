package com.lottery.controller;

import com.lottery.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public Result<Map<String, String>> health() {
        return Result.ok(Map.of("status", "UP", "service", "lottery-backend"));
    }
}
