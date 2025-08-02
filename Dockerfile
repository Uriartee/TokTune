# Usar imagen de OpenJDK con Alpine
FROM openjdk:17-jdk-alpine

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

# Copiar el JAR compilado
COPY target/*.jar app.jar

# Exponer puerto
EXPOSE 8080

# Variables de entorno
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Comando de inicio
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]