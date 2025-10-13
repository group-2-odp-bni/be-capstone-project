# =========================
# 🏗️ Stage 1: Build
# =========================
FROM gradle:8.10.2-jdk21-alpine AS build

# Set working dir
WORKDIR /app

# Copy Gradle build scripts and cache deps early
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# Pre-download dependencies
RUN gradle --no-daemon dependencies || true

# Copy the rest of the source
COPY src ./src

# Build jar (skip tests for speed)
RUN gradle --no-daemon bootJar

# =========================
# 🚀 Stage 2: Runtime
# =========================
FROM eclipse-temurin:21-jre-alpine

# Set working dir
WORKDIR /app

# Copy jar only (not build tools)
COPY --from=build /app/build/libs/*.jar app.jar

# Reduce image size further
RUN apk add --no-cache tini \
    && addgroup -S spring && adduser -S spring -G spring \
    && chown spring:spring /app/app.jar

USER spring

EXPOSE 8080

# Use tini as init process (graceful shutdown)
ENTRYPOINT ["/sbin/tini", "--"]

# Run Spring Boot
CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
