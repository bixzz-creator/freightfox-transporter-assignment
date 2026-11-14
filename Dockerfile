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

# Expose HTTP ports (Render defaults to 10000, standard containers use 8080)
EXPOSE 10000 8080

# JVM memory ergonomics, container support, and default port configuration
ENV PORT=10000 \
    SERVER_PORT=10000 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Actuator health check probe (evaluates dynamic PORT with 10000 fallback)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:${PORT:-10000}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_TOOL_OPTIONS -jar app.jar --server.port=${PORT:-10000} --server.address=0.0.0.0"]
