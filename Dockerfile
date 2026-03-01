# Multi-stage build for CZERTAINLY RabbitMQ Bootstrap

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy Maven wrapper first
COPY mvnw .
COPY .mvn .mvn

# Make mvnw executable
RUN chmod +x mvnw

# Copy pom.xml for better layer caching
COPY pom.xml .

# Download dependencies (cached if pom.xml doesn't change)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests

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
COPY --from=build /app/target/*.jar app.jar

# Allow mounting external configuration files (e.g., custom application.yml)
VOLUME /app/config

# Change ownership to non-root user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose the application port
EXPOSE 8077

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8077/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
