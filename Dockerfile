# Use a Maven image to build the application
FROM maven:3.9-eclipse-temurin-21 AS build
COPY . .
RUN mvn clean package -DskipTests

# Use an Eclipse Temurin JRE image to run the application
FROM eclipse-temurin:21-jre-jammy
COPY --from=build /target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
