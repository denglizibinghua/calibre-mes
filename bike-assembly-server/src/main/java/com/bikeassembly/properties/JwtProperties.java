package com.bikeassembly.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性类。
 */
@Data
@Component
@ConfigurationProperties(prefix = "bike.jwt")
public class JwtProperties {

    /**
     * 管理端 secret key
     */
    private String adminSecretKey;

    /**
     * 管理端 token 过期时间（毫秒）
     */
    private long adminTtl;

    /**
     * 管理端 token 请求头名称
     */
    private String adminTokenName;
}
