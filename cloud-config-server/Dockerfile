FROM gradle:8.10.2-jdk21-alpine AS build

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN gradle --no-daemon dependencies || true

COPY src ./src
RUN gradle --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring \
    && apk add --no-cache tini gcompat curl

COPY --from=build --chown=spring:spring /app/build/libs/*.jar app.jar

USER spring

# Set config home to writable directory (fixes JGit permission issue)
ENV XDG_CONFIG_HOME=/tmp

EXPOSE 8080

ENTRYPOINT ["/sbin/tini", "--"]

CMD ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:InitialRAMPercentage=50.0", \
    "-XX:+TieredCompilation", \
    "-XX:TieredStopAtLevel=1", \
    "-XX:+UseG1GC", \
    "-XX:MaxMetaspaceSize=128m", \
    "-Dspring.jmx.enabled=false", \
    "-Dspring.main.lazy-initialization=true", \
    "-jar", \
    "app.jar"]
