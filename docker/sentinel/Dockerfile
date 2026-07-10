FROM eclipse-temurin:8-jre-alpine
COPY sentinel-dashboard-1.8.6.jar /app.jar
EXPOSE 8858
ENTRYPOINT ["java", "-Dserver.port=8858", "-jar", "/app.jar"]
