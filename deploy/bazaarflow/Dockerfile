# Runtime-only image: the jar (app.jar) is built on a PC with `mvn package` in the BazaarFlow
# repository and copied next to this file, so the 1 GB server never has to run a Maven build.
FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home bazaarflow
WORKDIR /app
COPY app.jar app.jar
USER bazaarflow
EXPOSE 8080
# Tuned for a small server: capped heap, serial GC, light JIT.
ENV JAVA_TOOL_OPTIONS="-Xmx200m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "app.jar"]
