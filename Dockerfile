# Two stages: build the jar with Maven, then copy only the jar into a small Java runtime image.

# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline          # cached layer: re-runs only when pom.xml changes
COPY src ./src
RUN mvn -B -q package -DskipTests            # tests run in CI, not in every image build

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# Run as an unprivileged user, never as root.
RUN useradd --system --uid 10001 app
COPY --from=build /src/target/dsa-tracker-*.jar app.jar
USER app
EXPOSE 8080
# Use up to 75% of the container's memory for the Java heap.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
