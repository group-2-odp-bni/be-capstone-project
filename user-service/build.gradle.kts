plugins {
	java
	jacoco
    id("org.sonarqube") version "6.3.1.5724"
	id("com.google.protobuf") version "0.9.4"
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

extra["springCloudVersion"] = "2025.0.0"

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.kafka:spring-kafka")
	implementation("org.xerial.snappy:snappy-java:1.1.10.5")
	implementation("com.google.protobuf:protobuf-java:4.29.2")
	implementation("com.google.cloud:spring-cloud-gcp-starter-storage:6.5.0")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")

    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

	compileOnly("org.projectlombok:lombok")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.kafka:spring-kafka-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0")
	}
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
		artifact = "com.google.protobuf:protoc:4.29.2"
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

tasks.withType<Test>().configureEach {
    doFirst {
        configurations.testRuntimeClasspath.get()
            .find { it.name.contains("mockito-core") }
            ?.let { jvmArgs("-javaagent:${it.absolutePath}") }
    }
}

