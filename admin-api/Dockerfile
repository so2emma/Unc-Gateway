FROM eclipse-temurin:17-jre
WORKDIR /app
COPY admin-api/target/admin-api-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
