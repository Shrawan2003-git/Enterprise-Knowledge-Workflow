FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Cache wrapper and dependencies
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# Compile and package application
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# Minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S knowflow && adduser -S knowflow -G knowflow \
    && mkdir -p /app/storage \
    && chown -R knowflow:knowflow /app

USER knowflow:knowflow

COPY --chown=knowflow:knowflow --from=builder /workspace/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
