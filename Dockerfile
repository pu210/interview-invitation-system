FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY java-starter/.mvn java-starter/.mvn
COPY java-starter/mvnw java-starter/pom.xml java-starter/
COPY java-starter/src java-starter/src
COPY training-fixtures/data training-fixtures/data
WORKDIR /workspace/java-starter
RUN chmod +x mvnw && ./mvnw -B -Drecruitment.data-file=target/test-data/recruitment-state.json verify

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/java-starter/target/training-1.0.0.jar app.jar
EXPOSE 8080
CMD ["sh", "-c", "exec java -XX:MaxRAMPercentage=65 -jar app.jar --server.port=${PORT:-8080} --recruitment.data-file=${RECRUITMENT_DATA_FILE:-/app/data/recruitment-state.json}"]
