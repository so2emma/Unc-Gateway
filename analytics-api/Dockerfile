FROM eclipse-temurin:17-jre
WORKDIR /app
COPY analytics-api/target/analytics-api-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
