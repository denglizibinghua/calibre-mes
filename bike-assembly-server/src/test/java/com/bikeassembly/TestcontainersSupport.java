package com.bikeassembly;

/**
 * Testcontainers 1.20.4 的 docker-java 默认 API 版本仍是 1.32，
 * 而 Docker Engine 29 的最低 API 版本是 1.40，直接连接会得到 HTTP 400。
 * 在创建任何容器前设置 docker-java 的 api.version 系统属性。
 */
public final class TestcontainersSupport {

    private TestcontainersSupport() {
    }

    public static void configureDockerApiVersion() {
        if (System.getProperty("api.version") == null) {
            System.setProperty("api.version", "1.44");
        }
    }
}
