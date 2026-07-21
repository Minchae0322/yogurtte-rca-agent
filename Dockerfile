# 빌드 스테이지: gradle wrapper로 bootJar를 만든다
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 레이어를 소스와 분리해 캐시한다 - 소스만 바뀌면 의존성 다운로드를 건너뛴다
COPY gradlew build.gradle settings.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies --quiet || true

COPY src src
RUN ./gradlew --no-daemon bootJar

# 실행 스테이지: JRE + claude CLI
FROM eclipse-temurin:21-jre
WORKDIR /app

# RCA_LLM_PROVIDER=claude-cli일 때 컨테이너 안에서 구독 계정으로 분석하려면 claude CLI가 필요하다.
# 인증은 호스트에서 `claude setup-token`으로 발급한 장기 토큰을 .env의 CLAUDE_CODE_OAUTH_TOKEN으로 넘긴다.
# anthropic/openai provider만 쓸 거면 이 RUN 블록을 통째로 지워 이미지를 가볍게 해도 된다.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
 && apt-get install -y --no-install-recommends nodejs \
 && npm install -g @anthropic-ai/claude-code \
 && apt-get purge -y curl && apt-get autoremove -y \
 && apt-get clean && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
