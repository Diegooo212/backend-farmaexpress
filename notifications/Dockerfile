# syntax=docker/dockerfile:1
# Imagen del microservicio "notifications" (puerto 8083). Se construye desde la carpeta notifications/.
#   docker build -t farmaexpress-notifications ./notifications

# 1) Compilación con el JDK y el Maven Wrapper del proyecto (la caché de ~/.m2 acelera las siguientes).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 chmod +x mvnw && ./mvnw -B -q -DskipTests package \
    && cp target/farmaexpress-notifications-*.jar app.jar

# 2) Imagen final: solo el JRE y el .jar, ejecutado con un usuario sin privilegios.
FROM eclipse-temurin:21-jre
RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER spring
EXPOSE 8083
# La JVM usa como máximo el 75% de la memoria que Docker le asigne al contenedor.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# Sano cuando /actuator/health responde "UP" (bash puro: la imagen no trae curl).
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8083 && printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3 && grep -q "\"UP\"" <&3'
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
