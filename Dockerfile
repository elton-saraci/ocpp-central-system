FROM eclipse-temurin:17-jdk

# Create config directory and copy the application.yml into it
RUN mkdir /config
COPY src/main/resources/application.yml /config/application.yml

# Copy JAR file
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} /app.jar

EXPOSE 8080 7070

# Spring Boot will pick up /config/application.yml automatically
ENTRYPOINT ["java", "-jar", "/app.jar"]