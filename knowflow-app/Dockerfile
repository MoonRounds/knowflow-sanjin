FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY knowflow-app/pom.xml ./knowflow-app/pom.xml
COPY knowflow-app/.mvn ./knowflow-app/.mvn
COPY knowflow-app/mvnw ./knowflow-app/mvnw
RUN ./knowflow-app/mvnw \
    -s /workspace/knowflow-app/.mvn/settings.xml \
    -f /workspace/pom.xml \
    -pl knowflow-app \
    -DskipTests \
    dependency:go-offline

COPY knowflow-app/src ./knowflow-app/src
RUN ./knowflow-app/mvnw \
    -s /workspace/knowflow-app/.mvn/settings.xml \
    -f /workspace/pom.xml \
    -pl knowflow-app \
    -DskipTests \
    package

FROM eclipse-temurin:21-jre-jammy

RUN useradd --system --uid 10001 --create-home knowflow \
    && mkdir -p /var/lib/knowflow/files \
    && chown -R knowflow:knowflow /var/lib/knowflow
WORKDIR /app
COPY --from=build --chown=knowflow:knowflow \
    /workspace/knowflow-app/target/knowflow-app-0.1.0-SNAPSHOT.jar /app/knowflow-app.jar

USER knowflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/knowflow-app.jar"]
