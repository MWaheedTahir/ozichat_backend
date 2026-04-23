# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — BUILD
# Uses Maven + JDK 21 to compile the project and produce a fat JAR.
# This stage is discarded after the build; none of the Maven cache or
# source files end up in the final image.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy dependency descriptors first so Docker can cache the layer.
# Maven re-downloads dependencies only when pom.xml changes.
COPY pom.xml .
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -B --quiet

# Copy source code and build the fat JAR (skip tests — run them in CI separately)
COPY src ./src
RUN mvn package -DskipTests -B --quiet

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — RUNTIME
# Minimal JRE image.  No JDK, no Maven, no source code.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security — never run as root inside a container
RUN addgroup -S ozichat && adduser -S ozichat -G ozichat

WORKDIR /app

# Copy only the fat JAR from the build stage
COPY --from=builder /build/target/ozichat-backend-*.jar app.jar

# Directory for local media uploads (mounted as a volume in docker-compose)
RUN mkdir -p uploads && chown -R ozichat:ozichat /app

USER ozichat

# Expose the Spring Boot port
EXPOSE 8080

# ─────────────────────────────────────────────────────────────────────────────
# JVM tuning for containers:
#   -XX:+UseContainerSupport          → respect cgroup memory/CPU limits
#   -XX:MaxRAMPercentage=75.0         → use 75 % of container RAM for heap
#   -Djava.security.egd=...           → faster SecureRandom (important for JWT)
#   -Dspring.profiles.active=prod     → activates application-prod.yml
# ─────────────────────────────────────────────────────────────────────────────
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:prod}", \
  "-jar", "app.jar"]
