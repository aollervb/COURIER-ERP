# ---- build stage ----
FROM amazoncorretto:21 AS build
WORKDIR /app

# If your repo includes the Gradle Wrapper (recommended)
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

# Leverage Docker layer caching (download deps)
RUN chmod +x ./gradlew \
  && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Copy source and build
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- runtime stage ----
FROM amazoncorretto:21
WORKDIR /app

# Copy jar produced by Spring Boot Gradle plugin
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]