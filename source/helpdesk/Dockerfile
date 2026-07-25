# ===== Giai doan build =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

# ===== Giai doan chay =====
FROM eclipse-temurin:17-jre
WORKDIR /app

ENV TZ=Asia/Ho_Chi_Minh

# curl dung cho healthcheck cua Docker/Dokploy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/target/helpdesk-*.jar app.jar

# Chay bang user thuong cho an toan
RUN useradd -r -u 1001 -g root helpdesk
USER 1001

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
