FROM gradle:8.10.2-jdk21-alpine AS build

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN gradle --no-daemon dependencies || true

COPY src ./src
RUN gradle --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd -r spring && useradd -r -g spring spring \
    && apt-get update && apt-get install -y --no-install-recommends tini curl \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

COPY --from=build --chown=spring:spring /app/build/libs/*.jar app.jar

USER spring

EXPOSE 8080

ENTRYPOINT ["/usr/bin/tini", "--"]

CMD ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:InitialRAMPercentage=50.0", \
    "-XX:+TieredCompilation", \
    "-XX:TieredStopAtLevel=1", \
    "-XX:+UseG1GC", \
    "-XX:MaxMetaspaceSize=128m", \
    "-Dspring.jmx.enabled=false", \
    "-jar", \
    "app.jar"]
