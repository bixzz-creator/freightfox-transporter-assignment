# ==============================================================================
# Multi-Stage Dockerfile for Transporter Assignment on Lanes
# Stage 1: Build stage with OpenJDK / Temurin 21 and Maven
# Stage 2: Minimal, secure JRE 21 runtime
# ==============================================================================

# ----------------- Stage 1: Builder -----------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Maven Wrapper and POM first to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd pom.xml ./

# Convert line endings if built on Windows and grant execution rights
RUN chmod +x ./mvnw

# Download dependencies offline (cached layer)
RUN ./mvnw dependency:go-offline -B || true

# Copy source code
COPY src/ ./src/

# Package the application (skip tests during container build; tested separately in CI)
RUN ./mvnw clean package -DskipTests -B

# Extract fat jar layers for optimized container caching
WORKDIR /build/target/extracted
RUN java -Djarmode=layertools -jar /build/target/*.jar extract

# ----------------- Stage 2: Runtime -----------------
FROM eclipse-temurin:21-jre-alpine AS runner

# Create dedicated non-root application user for container security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Install curl for container health check
RUN apk add --no-cache curl

# Copy extracted layers from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/application/ ./

# Run as unprivileged user
USER appuser:appgroup

# Expose standard application port
EXPOSE 8080

# Environment variables with sensible defaults (overridable at container launch)
ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Container healthcheck using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

# Launch application using Spring Boot JarLauncher
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

