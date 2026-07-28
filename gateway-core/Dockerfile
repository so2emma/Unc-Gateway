FROM eclipse-temurin:17-jre
WORKDIR /app
COPY gateway-core/target/gateway-core-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
