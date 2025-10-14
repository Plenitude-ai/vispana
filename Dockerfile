# Stage 1: Build the JAR with Maven
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app
COPY pom.xml package.json webpack.config.js postcss.config.js tailwind.config.js ./
COPY src/ src/

# Build the application (includes frontend webpack build)
RUN mvn clean package

# Stage 2: Run the JAR with a lightweight image
FROM amazoncorretto:21.0.1

RUN mkdir /app
WORKDIR /app

# Copy only the built JAR & resources
# Needed because app.properties references file:src/main/resources/
# This includes the webpack-built bundle.js at src/main/resources/static/built/bundle.js
COPY --from=builder /app/src/main/resources/ src/main/resources/
# Copy the built JAR
COPY --from=builder /app/target/vispana-0.0.1-SNAPSHOT.jar target/

# Expose the port configured in application.properties
EXPOSE 4000

ENTRYPOINT ["java", "--enable-preview", "-jar", "target/vispana-0.0.1-SNAPSHOT.jar"]
