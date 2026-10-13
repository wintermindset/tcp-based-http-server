FROM eclipse-temurin:21-jdk@sha256:aab6acf426d88f29f524c27f1521c9a22b110436345ca075a98f2d85351eb777 AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN ./mvnw -B --no-transfer-progress package -DskipTests

FROM eclipse-temurin:21-jre@sha256:cff19e6215689161eb6162c11b86b0c60ddf802164f2eaf48d570f8fb79a36c5
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/tcp-based-http-server-1.0.0.jar app.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 CMD ["java", "-XX:MaxRAMPercentage=10.0", "-cp", "app.jar", "com.wintermindset.HealthCheck", "9090"]
ENTRYPOINT ["java", "-jar", "app.jar"]
