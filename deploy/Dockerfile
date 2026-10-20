# 后端运行镜像（jar 由 CI 构建后上传，镜像只负责运行，构建更快、层更小）
#
# 基础镜像可通过 build args 替换为国内镜像源，例如：
#   BASE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21-jre
ARG BASE_IMAGE=eclipse-temurin:21-jre
FROM ${BASE_IMAGE}

LABEL org.opencontainers.image.title="nova-admin-backend" \
      org.opencontainers.image.description="NOVA ADMIN 后端服务"

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    SPRING_PROFILES_ACTIVE=staging \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

WORKDIR /app

# SQLite 数据文件与日志目录（compose 中挂载为卷，容器重建不丢）
RUN mkdir -p /app/data /app/logs

COPY admin-backend.jar /app/app.jar

EXPOSE 8080

# exec：让 java 成为 PID 1，docker stop 能收到 SIGTERM 并优雅停机
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
