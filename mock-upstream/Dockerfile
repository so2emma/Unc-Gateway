FROM eclipse-temurin:17-jre
WORKDIR /app
COPY mock-upstream/target/mock-upstream-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
