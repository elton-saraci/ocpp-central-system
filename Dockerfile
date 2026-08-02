# ---- Build stage ----
# Compiles the application into a JAR inside Docker, so no pre-built
# target/ artifact is required (needed for Render and fresh clones,
# since target/ is gitignored).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the pom.xml first to leverage Docker layer caching for dependencies
COPY pom.xml .

# Download dependencies (non-fatal; speeds up the next step on rebuilds)
RUN mvn -B dependency:go-offline -q || true

# Copy sources and build the executable JAR
COPY src ./src
RUN mvn -B -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre

# Create config directory and copy the application.yml into it
RUN mkdir /config
COPY src/main/resources/application.yml /config/application.yml

# Copy the JAR produced by the build stage
COPY --from=build /app/target/*.jar /app.jar

EXPOSE 8080 7070

# Spring Boot will pick up /config/application.yml automatically
ENTRYPOINT ["java", "-jar", "/app.jar"]