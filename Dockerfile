# Build Stage
FROM gradle:8.5-jdk17 AS builder
WORKDIR /build

# 캐시 효율을 위해 gradle 설정 파일만 먼저 복사하여 의존성 다운로드
COPY build.gradle settings.gradle /build/
RUN gradle build -x test --no-daemon || true

# 전체 소스 복사 후 빌드 진행
COPY . /build
RUN gradle bootJar -x test --no-daemon

# Run Stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 보안상 root 사용자가 아닌 spring 사용자로 구동
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# 빌드 스테이지에서 생성된 jar 복사
COPY --from=builder /build/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
