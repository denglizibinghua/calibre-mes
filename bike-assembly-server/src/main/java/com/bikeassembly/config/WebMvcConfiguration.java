package com.bikeassembly.config;

import com.bikeassembly.interceptor.IdempotencyInterceptor;
import com.bikeassembly.interceptor.JwtTokenAdminInterceptor;
import com.bikeassembly.interceptor.JwtTokenAppInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final JwtTokenAdminInterceptor jwtTokenAdminInterceptor;
    private final JwtTokenAppInterceptor jwtTokenAppInterceptor;
    private final IdempotencyInterceptor idempotencyInterceptor;

    /** CORS 允许来源白名单，默认仅本机开发端口 */
    @Value("${bike.cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    /**
     * [答辩入口] JWT 拦截器注册。
     * [Token] 管理端和小程序端使用不同的拦截器实例，实现双通道隔离：
     *   - JwtTokenAdminInterceptor：拦截 /admin/**，使用 admin 密钥解析 JWT
     *   - JwtTokenAppInterceptor：拦截 /app/**，使用 app 密钥解析 JWT
     * [权限控制] 白名单（excludePathPatterns）：登录、退出、刷新 Token、健康检查不需要携带 Token
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // [幂等] 幂等键拦截器：在所有 JWT 拦截器之前执行，提取 Idempotency-Key 请求头
        log.info("注册幂等键拦截器...");
        registry.addInterceptor(idempotencyInterceptor)
                .addPathPatterns("/admin/**", "/app/**")
                .excludePathPatterns(
                        "/admin/user/login",
                        "/admin/user/face-login",
                        "/admin/user/logout",
                        "/admin/user/refresh-token",
                        "/app/user/wx-login",
                        "/app/user/login",
                        "/app/user/face-login",
                        "/app/user/refresh-token",
                        "/app/user/logout"
                );

        log.info("注册 JWT 拦截器...");
        // [Token] 管理端拦截器：拦截所有 /admin/** 请求
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns(
                        "/admin/user/login",          // 登录接口：不需要 Token
                        "/admin/user/face-login",
                        "/admin/user/logout",          // 退出接口：拦截器内部从 BaseContext 获取 tokenJti
                        "/admin/user/refresh-token",   // Token 刷新：使用 Refresh Token 而非 Access Token
                        "/admin/health",               // 健康检查
                        "/favicon.ico"
                );

        log.info("注册 App JWT 拦截器...");
        // [Token] 小程序端拦截器：拦截所有 /app/** 请求，白名单逻辑同上
        registry.addInterceptor(jwtTokenAppInterceptor)
                .addPathPatterns("/app/**")
                .excludePathPatterns(
                        "/app/user/wx-login",          // 微信登录：通过 code 换取 openid
                        "/app/user/login",             // App 端用户名密码登录
                        "/app/user/face-login",
                        "/app/user/refresh-token",     // App 端 Token 刷新
                        "/app/user/logout"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 静态资源放行
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}
