# Servidor del Departamento de Psicología (API + versión web de la app) listo para la nube.
# Compila el servidor dentro de Docker, así cualquier servicio de hosting puede construirlo desde
# el código. La versión web ya viene compilada en src/main/resources/static (compilar-todo.bat).

# --- Etapa 1: compilar ---
FROM maven:3.9-eclipse-temurin-17 AS compilacion
WORKDIR /codigo
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# --- Etapa 2: ejecutar ---
FROM eclipse-temurin:17-jre

# Las fechas se guardan en hora local de Paraguay (igual que el escritorio): sin esto el servidor
# en la nube usaría UTC y todas las citas quedarían corridas 3 horas.
ENV TZ=America/Asuncion
# SerialGC y tope de memoria: los planes gratuitos (Render) dan 512 MB.
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=America/Asuncion -XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"

# Carpeta de adjuntos dentro del contenedor (conviene montarla como disco persistente).
ENV ADJUNTOS_CARPETA=/datos/adjuntos
RUN mkdir -p /datos/adjuntos && useradd --system --home /app servidor && chown -R servidor /datos

WORKDIR /app
COPY --from=compilacion /codigo/target/backend-api-0.1.0.jar servidor.jar
USER servidor

EXPOSE 8080
CMD ["java", "-jar", "servidor.jar"]
