package com.bikeassembly.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Configuration
public class AiHttpClientConfiguration {

    @Bean
    public RestClientCustomizer aiRestClientTimeoutCustomizer(
            @Value("${ai.timeout-seconds:120}") long timeoutSeconds) {
        return builder -> {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 30)));
            requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
            builder.requestFactory(requestFactory);
        };
    }
}
