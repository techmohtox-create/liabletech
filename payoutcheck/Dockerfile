# One image with the Angular UI served by the Spring Boot API.
FROM node:22-alpine AS web
WORKDIR /src/web
COPY web/package.json web/package-lock.json web/.npmrc ./
RUN npm ci
COPY web/ ./
RUN npx ng build

FROM eclipse-temurin:21-jdk AS api
WORKDIR /src/api
COPY api/.mvn .mvn
COPY api/mvnw api/pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY api/src src
COPY --from=web /src/api/src/main/resources/static src/main/resources/static
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home payoutcheck
WORKDIR /app
COPY --from=api /src/api/target/payoutcheck-*.jar app.jar
USER payoutcheck
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "app.jar"]
