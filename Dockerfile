# ---- Build Stage ----
# Use full JDK image to compile and package the application
# alpine variant is smaller than the standard image
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper and config first — Docker layer caching means
# these layers only rebuild if pom.xml or wrapper files change
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Copy source code and build the JAR, skipping tests
# Tests run in CI — no need to run them again during Docker build
COPY src src
RUN chmod +x mvnw && ./mvnw package -DskipTests

# ---- Runtime Stage ----
# Use JRE-only image — no compiler or build tools needed at runtime
# Results in a significantly smaller final image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy only the built JAR from the build stage — not the entire source tree
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]