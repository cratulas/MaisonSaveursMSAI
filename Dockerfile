# ============================
# FASE 1: Build con Maven
# ============================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copiamos POM y luego el código fuente
COPY pom.xml .
COPY src ./src

# Copiamos también la carpeta firebase (para el serviceAccountKey.json)
# Asegúrate de que en tu proyecto exista: /firebase/serviceAccountKey.json
COPY firebase ./firebase

# Compilamos y empaquetamos (sin tests para ir más rápido)
RUN mvn clean package -DskipTests

# ============================
# FASE 2: Imagen liviana para runtime
# ============================
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copiamos el jar generado por el build
# OJO: el nombre debe coincidir con el <artifactId> y <version> de tu pom.xml
COPY --from=build /app/target/ia-service-0.0.1-SNAPSHOT.jar app.jar

# Copiamos la carpeta firebase tal como la espera application.properties
COPY --from=build /app/firebase ./firebase

# Exponemos el puerto del microservicio IA
EXPOSE 8083

# Arrancamos la app
ENTRYPOINT ["java", "-jar", "app.jar"]
