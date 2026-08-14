# Build stage
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml .
COPY src ./src

RUN mvn clean package dependency:copy-dependencies -DskipTests -DincludeScope=runtime -q

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /build/target/thomas-anders-four-java-*.jar app.jar
COPY --from=builder /build/target/dependency/ /app/libs/

# Copy the Gaffer source data and startup pipeline script
COPY matrix_characters.csv /app/matrix_characters.csv
COPY run-gaffer-pipeline.sh /app/run-gaffer-pipeline.sh
RUN sed -i 's/\r$//' /app/run-gaffer-pipeline.sh && chmod +x /app/run-gaffer-pipeline.sh

# Set default Neo4j connection
ENV NEO4J_URI=bolt://neo4j:7687
ENV NEO4J_USER=neo4j
ENV NEO4J_PASSWORD=neo4jpassword

# Run conversion first, then import on every container startup
ENTRYPOINT ["/bin/sh", "/app/run-gaffer-pipeline.sh"]
