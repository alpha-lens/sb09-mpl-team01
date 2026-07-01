# ===== 1단계: 빌드 환경 (Builder Stage) =====
FROM amazoncorretto:17 AS builder
WORKDIR /build

# 1. [레이어 캐싱 핵심] 의존성 관련 파일만 먼저 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x ./gradlew

# 2. 소스 코드 넣기 전에 라이브러리 미리 다운로드 (캐시 활용)
RUN ./gradlew dependencies --no-daemon

# 3. 소스 코드 복사 후 jar 빌드
COPY src src
RUN ./gradlew bootJar -x test --no-daemon


# ===== 2단계: 실행 환경 (Runtime Stage) =====
FROM amazoncorretto:17-alpine
WORKDIR /app

# 보안상 root가 아닌 spring 사용자로 구동
RUN addgroup -S spring && adduser -S spring -G spring

ENV JVM_OPTS=""

# 4. builder 단계 산출물(jar)만 복사
COPY --from=builder /build/build/libs/*-SNAPSHOT.jar app.jar

# jar 소유권을 spring 사용자로 변경 후 전환
RUN chown spring:spring app.jar
USER spring:spring

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JVM_OPTS -Dspring.profiles.active=prod -jar app.jar"]
