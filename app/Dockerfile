# Multi-stage build: compile with full Maven+JDK, run on a slim JRE only.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/app-0.0.1-SNAPSHOT.jar app.jar

# Render injects $PORT at container start - shell form so it expands at runtime, not build time.
# JAVA_OPTS is optional (e.g. "-Xmx400m" on Render's free tier, which caps containers at 512MB).
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=${PORT:-8080}"]
