# syntax=docker/dockerfile:1

# ---- Etapa 1: compilar el .jar con Maven ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Primero solo lo necesario para descargar dependencias: si el pom.xml no cambia,
# Docker reutiliza esta capa y no vuelve a descargarlas en cada build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
# Los tests ya los ejecuta el CI; aquí solo se empaqueta
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp package -DskipTests \
    && cp target/*.jar app.jar

# ---- Etapa 2: imagen final, solo con el JRE y el .jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# La aplicación no se ejecuta como root dentro del contenedor
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/app.jar app.jar
USER app

EXPOSE 8080
# La JVM usa como máximo el 75 % de la memoria que Docker asigne al contenedor
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
