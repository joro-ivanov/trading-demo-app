# Runtime image. Assumes the executable '-boot' jar was built by `mvn -DskipTests package`.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/*-boot.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
