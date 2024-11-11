FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY ./ /build/
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:21-alpine AS build-image
WORKDIR /app
COPY --from=build /build/target/trailence-*-spring-boot.jar /app/trailence.jar
ENTRYPOINT ["java","-jar","/app/trailence.jar"]