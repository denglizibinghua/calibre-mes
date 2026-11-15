package com.bikeassembly.config;

import com.bikeassembly.interceptor.AntiCrawlerInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(AntiCrawlerProperties.class)
public class AntiCrawlerConfiguration implements WebMvcConfigurer {
    private final AntiCrawlerInterceptor antiCrawlerInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(antiCrawlerInterceptor)
                .addPathPatterns("/admin/**", "/app/**")
                .excludePathPatterns("/admin/health", "/favicon.ico");
    }
}
