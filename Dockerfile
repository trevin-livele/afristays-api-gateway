FROM openjdk:21-jdk-slim
WORKDIR /app
COPY target/afristays-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 31304
ENTRYPOINT ["java", "-jar", "app.jar"]


