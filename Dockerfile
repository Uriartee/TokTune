# Multi-stage build para compilar y ejecutar
# Etapa 1: Compilación
FROM maven:3.9-eclipse-temurin-17-alpine AS build

# Crear directorio de trabajo
WORKDIR /app

# Copiar archivos del proyecto
COPY pom.xml .
COPY src src

# Compilar la aplicación
RUN mvn clean package -DskipTests

# Etapa 2: Runtime
FROM eclipse-temurin:17-jre-alpine

# Instalar dependencias del sistema
RUN apk update && \
    apk upgrade && \
    apk add --no-cache \
    python3 \
    py3-pip \
    ffmpeg \
    wget \
    curl \
    bash

# Instalar yt-dlp
RUN python3 -m pip install --upgrade pip && \
    python3 -m pip install yt-dlp

# Crear enlaces simbólicos para asegurar que esté en PATH
RUN ln -sf $(which yt-dlp) /usr/local/bin/yt-dlp 2>/dev/null || true && \
    ln -sf $(which yt-dlp) /usr/bin/yt-dlp 2>/dev/null || true

# Verificar instalaciones
RUN echo "=== VERIFICANDO INSTALACIONES ===" && \
    python3 --version && \
    python3 -m yt_dlp --version && \
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

# Variables de entorno
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Comando de inicio
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]