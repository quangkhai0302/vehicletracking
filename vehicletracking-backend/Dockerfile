FROM maven:3.9.16-eclipse-temurin-26 AS build

WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package \
    && find target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' -exec cp '{}' target/app.jar \;

FROM eclipse-temurin:26-jre

WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring
COPY --from=build --chown=spring:spring /workspace/target/app.jar /app/app.jar

USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
