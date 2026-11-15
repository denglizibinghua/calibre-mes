# ============================================================
# 后端 bike-assembly-server 多阶段构建
# 构建：maven:3.9 + Temurin 17
# 运行：Temurin 17 JRE（含 curl 供容器健康检查）
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先复制 POM 以复用依赖层缓存
COPY pom.xml .
COPY bike-assembly-common/pom.xml bike-assembly-common/pom.xml
COPY bike-assembly-pojo/pom.xml bike-assembly-pojo/pom.xml
COPY bike-assembly-server/pom.xml bike-assembly-server/pom.xml
RUN mvn -B -q dependency:go-offline -pl bike-assembly-server -am || true

# 复制源码并打包（测试由 CI 阶段执行）
COPY . .
RUN mvn -B -q package -Dmaven.test.skip=true -pl bike-assembly-server -am

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/bike-assembly-server/target/bike-assembly-server-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
