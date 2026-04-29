# ──────────────────────────────────────────────────
# EpsteinLens — Multi-stage Dockerfile
# Stage 1: Build the Spring Boot fat JAR
# Stage 2: Build the React frontend
# Stage 3: Production runtime (JRE + Nginx)
# ──────────────────────────────────────────────────

# ── Stage 1: Backend Build ──
FROM eclipse-temurin:21-jdk AS backend-build
WORKDIR /app

# Cache Gradle wrapper
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

# Build the app
COPY src/ src/
RUN ./gradlew --no-daemon bootJar -x test \
    && cp build/libs/*.jar app.jar

# ── Stage 2: Frontend Build ──
FROM node:22-alpine AS frontend-build
WORKDIR /app

COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci --silent

COPY frontend/ .
RUN npm run build

# ── Stage 3: Production Runtime ──
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Install Tesseract OCR + language packs for multi-language OCR
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       tesseract-ocr \
       tesseract-ocr-eng \
       tesseract-ocr-fra \
       tesseract-ocr-spa \
    && rm -rf /var/lib/apt/lists/*

# Copy the backend JAR
COPY --from=backend-build /app/app.jar app.jar

# Copy frontend build into Spring Boot's static resources
# Spring Boot will serve these automatically from /static
COPY --from=frontend-build /app/dist/ /app/static/

# Health check — Spring Actuator endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.awt.headless=true"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.resources.static-locations=file:/app/static/"]
