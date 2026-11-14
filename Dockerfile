# ==============================================================================
# Multi-Stage Production Dockerfile for Transporter Assignment Platform
# Stage 1: Build stage with Eclipse Temurin OpenJDK 21 & Maven
# Stage 2: Minimal, secure Alpine JRE 21 runtime
# ==============================================================================

# ----------------- Stage 1: Builder -----------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Install Maven package manager
RUN apk add --no-cache maven

# Copy POM first to leverage Docker layer caching
COPY pom.xml ./
COPY src/ ./src/

# Package the production artifact (skip tests during container packaging)
RUN mvn clean package -DskipTests -B

# ----------------- Stage 2: Runtime -----------------
FROM eclipse-temurin:21-jre-alpine AS runner

# Create dedicated unprivileged user for security compliance
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Install curl for Actuator health checks
RUN apk add --no-cache curl

# Copy fat JAR directly from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/*.jar app.jar

# Run as non-root user
USER appuser:appgroup

# Expose HTTP port (Render, Railway, AWS map this port automatically)
EXPOSE 8080

# JVM memory ergonomics and container support
ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Actuator health check probe
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
