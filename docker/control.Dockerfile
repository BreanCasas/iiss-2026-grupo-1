FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml .
COPY modules ./modules

RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY --from=build /workspace/modules/engine/target/engine.jar /app/engine.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/engine.jar"]