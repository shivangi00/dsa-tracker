# Used by Render (DEPLOY.md, section 5). Two stages: build the jar with Maven, then copy only the
# jar into a small Java runtime image.

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
# Memory settings that fit Render's free 512 MB instance with room to spare:
#   MaxRAMPercentage=60   heap up to 60% of the container's memory (about 300 MB of 512 MB)
#   MaxMetaspaceSize      caps the memory used for loaded classes
#   Xss512k               smaller stack per thread (the default is 1 MB)
#   UseSerialGC           the simplest garbage collector, right for a small, single-CPU instance
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:MaxMetaspaceSize=160m -Xss512k -XX:+UseSerialGC"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
