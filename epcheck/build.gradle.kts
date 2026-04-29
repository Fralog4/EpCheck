plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	id("org.springframework.boot") version "4.0.2"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.epcheck"
version = "0.0.1-SNAPSHOT"
description = "Ep-Check"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-neo4j")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

	// 1. Apache PDFBox (Text Extraction)
	implementation("org.apache.pdfbox:pdfbox:3.0.1")

	// 2. Stanford CoreNLP (NER)
	implementation("edu.stanford.nlp:stanford-corenlp:4.5.1")
	implementation("edu.stanford.nlp:stanford-corenlp:4.5.1:models-english")
	testImplementation("edu.stanford.nlp:stanford-corenlp:4.5.1:models-english")

	// 3. Iconic Logging
	implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")

	// 4. OpenAPI / Swagger UI
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

	// 5. Tesseract OCR (scanned PDF fallback)
	implementation("net.sourceforge.tess4j:tess4j:5.18.0")

	// 6. Monitoring — Spring Boot Actuator + Prometheus
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-registry-prometheus")

	// 7. Event-Driven Architecture — Kafka
	implementation("org.springframework.kafka:spring-kafka")

	developmentOnly("org.springframework.boot:spring-boot-devtools")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	// Test dependencies
	testImplementation("org.springframework.boot:spring-boot-starter-data-neo4j-test")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	// MockMultipartFile for batch endpoint
	implementation("org.springframework:spring-test")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
	maxHeapSize = "2048m" // CoreNLP needs at least 1.5GB to load models comfortably
}

tasks.register<JavaExec>("verifyPdf") {
	mainClass.set("com.source.epcheck.util.Verification")
	classpath = sourceSets["test"].runtimeClasspath
	jvmArgs("-Xmx2048m")
}
