FROM gradle:8.10.2-jdk21-alpine AS build

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./
COPY gradle.properties ./
COPY gradle ./gradle

# Download dependencies (with retry and verbose output)
RUN echo "=== Downloading dependencies ===" \
    && gradle --no-daemon dependencies --info || true

COPY src ./src

# Build the application with verbose output
RUN echo "=== Building application ===" \
    && gradle --no-daemon bootJar -x test --info --stacktrace \
    && echo "=== Build successful ===" \
    && ls -lah build/libs/

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create user and install runtime dependencies
RUN addgroup -S spring && adduser -S spring -G spring \
    && apk add --no-cache tini gcompat curl \
    && echo "Runtime dependencies installed"

COPY --from=build --chown=spring:spring /app/build/libs/*.jar app.jar

RUN ls -lah app.jar && echo "JAR file copied successfully"

USER spring

EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["/sbin/tini", "--"]

CMD ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=60.0", \
    "-XX:InitialRAMPercentage=50.0", \
    "-XX:+TieredCompilation", \
    "-XX:TieredStopAtLevel=1", \
    "-XX:+UseG1GC", \
    "-XX:MaxMetaspaceSize=128m", \
    "-Dspring.jmx.enabled=false", \
    "-Dspring.main.lazy-initialization=true", \
    "-jar", \
    "app.jar"]
