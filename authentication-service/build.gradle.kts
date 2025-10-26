plugins {
    java
    jacoco
    id("org.sonarqube") version "6.3.1.5724"
    id("com.google.protobuf") version "0.9.5"
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = property("group") as String
version = property("version") as String
description = property("description") as String

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("org.projectlombok:lombok")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.xerial.snappy:snappy-java:1.1.10.5")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.flywaydb:flyway-core")

    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.82")
    implementation("com.google.protobuf:protobuf-java:4.31.1")
    implementation("com.google.protobuf:protobuf-java-util:4.31.1")

    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    compileOnly("org.projectlombok:lombok")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

jacoco {
    toolVersion = "0.8.12"
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.31.1"
    }
}

sonar {
    properties {
        property("sonar.sources", "src/main/java")
        property("sonar.tests", "src/test/java")
        property("sonar.java.binaries", "build/classes/java/main,build/classes")
        property("sonar.java.test.binaries", "build/classes/java/test")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
        property("sonar.exclusions", "**/generated/**,**/*Proto.java,**/*OuterClass.java")
        property("sonar.coverage.exclusions", "**/generated/**,**/*Proto.java,**/*OuterClass.java")
    }
}

sourceSets {
    main {
        proto {
            srcDir("src/main/proto")
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Test>().configureEach {
    doFirst {
        configurations.testRuntimeClasspath.get()
            .find { it.name.contains("mockito-core") }
            ?.let { jvmArgs("-javaagent:${it.absolutePath}") }
    }
}

