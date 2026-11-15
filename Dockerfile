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
RUN groupadd --system bank && useradd --system --gid bank --no-create-home bank
WORKDIR /app
COPY --from=build /app/target/minibank-*.jar app.jar
USER bank
EXPOSE 8080
# Cek kesehatan tanpa curl/wget: buka koneksi ke port aplikasi lewat bash, lalu cari status UP.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/${PORT:-8080} && printf "GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && grep -q "\"UP\"" <&3' || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
