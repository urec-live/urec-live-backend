FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package \
    && cp target/urec-live-backend-0.0.1-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/app.jar ./app.jar

EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
