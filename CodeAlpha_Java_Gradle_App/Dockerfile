# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy the build scripts first and download dependencies in their own layer.
# This layer is reused as long as the build files don't change.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --quiet dependencies > /dev/null

COPY src ./src
# Tests already ran in CI before the image is built
RUN ./gradlew --no-daemon --quiet installDist -x test

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S -G app -u 10001 app
WORKDIR /opt/task-tracker
COPY --from=build /workspace/build/install/task-tracker/ ./

USER 10001

ENV PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
  CMD ["wget", "-q", "-O", "/dev/null", "http://127.0.0.1:8080/health"]

ENTRYPOINT ["./bin/task-tracker"]
