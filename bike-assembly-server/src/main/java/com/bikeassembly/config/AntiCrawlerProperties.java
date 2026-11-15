package com.bikeassembly.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "security.anti-crawler")
public class AntiCrawlerProperties {
    private boolean enabled = true;
    private int windowSeconds = 60;
    private int maxRequests = 120;
    private int loginMaxRequests = 10;
    private int aiMaxRequests = 30;
    private List<String> protectedPrefixes = new ArrayList<>(List.of(
            "/admin/user/login", "/app/user/login", "/app/user/wx-login",
            "/admin/ai", "/admin/export", "/app/export"));
}
