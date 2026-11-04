FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /src/target/daba-backend-*.jar app.jar
EXPOSE 8080
# Tas JVM : 75 % de la mémoire du conteneur (mem_limit du compose), le reste pour la pile, le métaspace et les
# tampons natifs. Les calculs Argon2 (19 MiB chacun) sont plafonnés par daba.mots-de-passe.calculs-simultanes.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
