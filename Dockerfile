FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B verify

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 spring
COPY --from=build /workspace/target/rag-pipeline-service-*.jar app.jar
USER spring
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=20s --retries=20 \
    CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
