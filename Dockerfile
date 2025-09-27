# Stage 1: Build the JAR with Maven
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app
COPY pom.xml package.json webpack.config.js ./
COPY src/ src/

RUN mvn package 

# Stage 2: Run the JAR with a lightweight image
FROM amazoncorretto:21.0.1

RUN mkdir /app

# COPY src/ /app/src/
# Copy only the built JAR from the builder stage
COPY --from=builder /app/target/vispana-0.0.1-SNAPSHOT.jar /app/target/
#COPY src /app

WORKDIR /app

# Expose the port that your Spring Boot application listens on (default is 8080)
EXPOSE 4000

ENTRYPOINT ["java","--enable-preview", "-jar", "target/vispana-0.0.1-SNAPSHOT.jar"]
