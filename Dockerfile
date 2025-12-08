# Multi-stage build for CZERTAINLY RabbitMQ Bootstrap

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copy pom.xml first for better layer caching
COPY pom.xml .

# Download dependencies (cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

# Add labels for better image documentation
LABEL maintainer="CZERTAINLY"
LABEL description="RabbitMQ Bootstrap Service for CZERTAINLY Platform"
LABEL version="1.0-SNAPSHOT"

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/target/rabbitBootstrap-1.0-SNAPSHOT.jar app.jar

# Copy definitions file (if it exists in resources, it's already in JAR)
# But allow overriding via volume mount
VOLUME /app/config

# Change ownership to non-root user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose the application port
EXPOSE 8077

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8077/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]