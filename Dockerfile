# Tahap build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package -DskipTests

# Tahap jalan: user non-root. Pakai image Ubuntu (bukan alpine) karena tersedia untuk
# x86 dan ARM, jadi bisa jalan di laptop, CI, maupun server ARM seperti SnapDeploy.
FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system bank && useradd --system --gid bank --no-create-home bank
WORKDIR /app
COPY --from=build /app/target/minibank-*.jar app.jar
USER bank
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s \
  CMD curl -fs "http://localhost:${PORT:-8080}/actuator/health" || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
