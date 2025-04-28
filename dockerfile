# Use lightweight OpenJDK 17 image
FROM openjdk:17-jdk-slim

# Working directory inside container
WORKDIR /app

# Copy the built JAR file
COPY target/pulsemonitor-1.0.0-SNAPSHOT.jar app.jar

# Expose port 8888
EXPOSE 8888

# Run the JAR file
ENTRYPOINT ["java", "-jar", "app.jar"]
