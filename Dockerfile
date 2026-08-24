# Build stage
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml .
COPY src ./src

RUN mvn clean package dependency:copy-dependencies -DskipTests -DincludeScope=runtime -q

# Runtime stage for gaffer-batch service
FROM eclipse-temurin:17-jre AS runtime

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /build/target/thomas-anders-four-java-*.jar app.jar
COPY --from=builder /build/target/dependency/ /app/libs/

# Copy the startup pipeline script
COPY run-gaffer-pipeline.sh /app/run-gaffer-pipeline.sh
RUN sed -i 's/\r$//' /app/run-gaffer-pipeline.sh && chmod +x /app/run-gaffer-pipeline.sh

# Set default Neo4j connection
ENV NEO4J_URI=bolt://neo4j:7687
ENV NEO4J_USER=neo4j
ENV NEO4J_PASSWORD=neo4jpassword

# Run conversion first, then import on every container startup
ENTRYPOINT ["/bin/sh", "/app/run-gaffer-pipeline.sh"]

# Neo4j init stage: includes cypher-shell plus Java import tooling
FROM neo4j:5.6 AS neo4j-init-runner

WORKDIR /app
COPY --from=builder /build/target/thomas-anders-four-java-*.jar app.jar
COPY --from=builder /build/target/dependency/ /app/libs/
