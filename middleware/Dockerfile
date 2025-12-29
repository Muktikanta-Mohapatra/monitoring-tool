FROM maven:3.9.4-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

COPY --from=builder /build/target/logforwarder-middleware-1.0.0.jar app.jar

RUN chmod +x app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms512m -Xmx2g"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
