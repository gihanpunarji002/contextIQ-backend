FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN chmod +x mvnw && ./mvnw -B package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build --chown=app:app /build/target/ContextIQ-0.0.1-SNAPSHOT.war app.war
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.war"]
