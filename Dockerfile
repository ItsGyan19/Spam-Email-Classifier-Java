# Multi-stage build for Spam Email Classifier Java Spring Boot
# Stage 1: Build JAR with Maven
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal Runtime JRE
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/spam-email-classifier-1.0.0.jar app.jar

EXPOSE 8080
ENV PORT=8080

# Use container support & bound heap to 75% RAM for cloud free tiers (e.g. 512MB)
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
