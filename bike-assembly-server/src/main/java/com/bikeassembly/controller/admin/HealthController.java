package com.bikeassembly.controller.admin;

import com.bikeassembly.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class HealthController {

    @GetMapping("/health")
    public Result<Map<String, String>> health() {
        return Result.success(Map.of(
                "status", "ok",
                "message", "bike-assembly-mes is running"
        ));
    }
}
