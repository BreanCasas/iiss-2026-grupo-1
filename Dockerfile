# --- Etapa de build ---
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /build

# POM general
COPY pom.xml .

# POM de cada módulo
COPY src/generator/pom.xml src/generator/pom.xml
COPY src/subscriber/pom.xml src/subscriber/pom.xml

# Código fuente
COPY src/generator/src src/generator/src
COPY src/subscriber/src src/subscriber/src

# Compila todos los módulos desde el POM general
RUN mvn -q clean package -DskipTests


# --- Imagen del Generator ---
FROM eclipse-temurin:25-jre-alpine AS generator

WORKDIR /app

COPY --from=build \
    /build/src/generator/target/generator-jar-with-dependencies.jar \
    ./generator.jar

ENTRYPOINT ["java", "-jar", "generator.jar"]


# --- Imagen del Subscriber ---
FROM eclipse-temurin:25-jre-alpine AS subscriber

WORKDIR /app

COPY --from=build \
    /build/src/subscriber/target/subscriber-jar-with-dependencies.jar \
    ./subscriber.jar

ENTRYPOINT ["java", "-jar", "subscriber.jar"]