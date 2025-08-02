# Multi-stage build para compilar y ejecutar
# Etapa 1: Compilación
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

# Copiar pom.xml primero para cache de dependencias
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar código y compilar
COPY src src
RUN mvn clean package -DskipTests -Dmaven.compiler.fork=false

# Etapa 2: Runtime
FROM eclipse-temurin:17-jre-alpine

# Instalar dependencias básicas
RUN apk update && apk upgrade && \
    apk add --no-cache \
    ffmpeg \
    wget \
    curl \
    bash \
    python3

# Instalar yt-dlp directamente desde releases de GitHub (más confiable)
RUN wget -O /usr/local/bin/yt-dlp https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp && \
    chmod +x /usr/local/bin/yt-dlp

# Verificar instalaciones
RUN echo "=== VERIFICANDO INSTALACIONES ===" && \
    python3 --version && \
    yt-dlp --version && \
    ffmpeg -version 2>&1 | head -1 && \
    echo "=== INSTALACIONES OK ==="

# Crear directorio de trabajo
WORKDIR /app

# Crear directorio para archivos de audio
RUN mkdir -p /app/songs

# Copiar el JAR desde la etapa de build
COPY --from=build /app/target/*.jar app.jar

# Exponer puerto
EXPOSE 8080

# Variables de entorno optimizadas para Railway
ENV JAVA_OPTS="-Xmx256m -Xms128m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Comando de inicio
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]