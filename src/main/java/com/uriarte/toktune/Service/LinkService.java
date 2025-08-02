package com.uriarte.toktune.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

@Service
public class LinkService {

    private final String auddApiToken;

    public LinkService(@Value("${audd.api.token}") String auddApiToken) {
        this.auddApiToken = auddApiToken;
    }

    private static final List<String> ALLOWED_DOMAINS = Arrays.asList(
            "youtube.com", "www.youtube.com", "youtu.be", "m.youtube.com",
            "tiktok.com", "www.tiktok.com", "vm.tiktok.com",
            "instagram.com", "www.instagram.com",
            "facebook.com", "www.facebook.com", "m.facebook.com", "fb.watch"
    );

    private boolean isValidUrl(String urlString) {
        try {
            if (urlString == null || urlString.trim().isEmpty()) {
                return false;
            }

            if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                urlString = "https://" + urlString;
            }

            URL url = new URL(urlString);
            String host = url.getHost().toLowerCase();
            return ALLOWED_DOMAINS.contains(host);

        } catch (Exception e) {
            System.out.println("⚠️ URL inválida: " + urlString);
            return false;
        }
    }

    public String filterJson(String resultjson) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(resultjson);

        JsonNode result = root.path("result");
        if (result.isMissingNode()) {
            return "No se encontró resultado";
        }

        String artist = result.path("artist").asText();
        String title = result.path("title").asText();
        String album = result.path("album").asText();
        String releaseDate = result.path("release_date").asText();
        String songLink = result.path("song_link").asText();

        String appleMusicLink = result.path("apple_music").path("url").asText();
        String spotifyLink = result.path("spotify").path("external_urls").path("spotify").asText();

        return String.format(
                "Artista: %s\nTítulo: %s\nÁlbum: %s\nFecha de lanzamiento: %s\nLink canción: %s\nApple Music: %s\nSpotify: %s",
                artist, title, album, releaseDate, songLink, appleMusicLink, spotifyLink
        );
    }

    public String callAuddApi(String songPath) {
        File songFile = new File(songPath);

        try {
            OkHttpClient client = new OkHttpClient();

            MultipartBody data = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", songFile.getName(),
                            RequestBody.create(songFile, MediaType.parse("audio/mp3")))
                    .addFormDataPart("return", "apple_music,spotify")
                    .addFormDataPart("api_token", auddApiToken)
                    .build();

            Request request = new Request.Builder()
                    .url("https://api.audd.io/")
                    .post(data)
                    .build();

            Response response = client.newCall(request).execute();
            String result = response.body().string();
            System.out.println("Respuesta Audd: " + result);

            String filteredResult = filterJson(result);

            // Eliminar el archivo después de procesarlo
            if (songFile.exists()) {
                if (songFile.delete()) {
                    System.out.println("🗑️ Archivo eliminado: " + songPath);
                } else {
                    System.out.println("⚠️ No se pudo eliminar el archivo: " + songPath);
                }
            }

            return filteredResult;

        } catch (Exception e) {
            e.printStackTrace();
            if (songFile.exists()) {
                songFile.delete();
            }
            return "Error al procesar el audio";
        }
    }

    private String formatTime(String minute, String second) {
        int min = 0;
        int sec = 0;

        try {
            if (minute != null && !minute.isEmpty()) {
                min = Integer.parseInt(minute);
            }
            if (second != null && !second.isEmpty()) {
                sec = Integer.parseInt(second);
            }
        } catch (NumberFormatException e) {
            min = 0;
            sec = 0;
        }

        return String.format("00:%02d:%02d", min, sec);
    }

    /**
     * Encontrar el comando correcto de yt-dlp
     */
    private ProcessBuilder getYtDlpProcessBuilder(String startTime, String songPath, String url) {
        String os = System.getProperty("os.name").toLowerCase();
        System.out.println("Sistema operativo detectado: " + os);

        if (os.contains("win")) {
            // Windows - usar yt-dlp.exe
            return new ProcessBuilder(
                    "yt-dlp.exe",
                    "-x",
                    "--audio-format", "mp3",
                    "--postprocessor-args", "ffmpeg:-ss " + startTime + " -t 10",
                    "-o", songPath,
                    "--no-playlist",
                    "--no-warnings",
                    url
            );
        } else {
            // Linux - probar diferentes opciones
            String[][] commands = {
                    // Opción 1: yt-dlp directo
                    {"yt-dlp", "-x", "--audio-format", "mp3", "--postprocessor-args", "ffmpeg:-ss " + startTime + " -t 10", "-o", songPath, "--no-playlist", "--no-warnings", url},
                    // Opción 2: yt-dlp con ruta completa
                    {"/usr/local/bin/yt-dlp", "-x", "--audio-format", "mp3", "--postprocessor-args", "ffmpeg:-ss " + startTime + " -t 10", "-o", songPath, "--no-playlist", "--no-warnings", url},
                    // Opción 3: Python módulo
                    {"python3", "-m", "yt_dlp", "-x", "--audio-format", "mp3", "--postprocessor-args", "ffmpeg:-ss " + startTime + " -t 10", "-o", songPath, "--no-playlist", "--no-warnings", url}
            };

            // Probar cada comando para ver cuál funciona
            for (String[] command : commands) {
                try {
                    ProcessBuilder testPb = new ProcessBuilder(command[0]);
                    if (command[0].equals("python3")) {
                        testPb.command().add("-m");
                        testPb.command().add("yt_dlp");
                    }
                    testPb.command().add("--version");

                    Process testProcess = testPb.start();
                    int exitCode = testProcess.waitFor();

                    if (exitCode == 0) {
                        System.out.println("✅ Comando encontrado: " + String.join(" ", command));
                        return new ProcessBuilder(command);
                    }
                } catch (Exception e) {
                    System.out.println("❌ Comando falló: " + command[0] + " - " + e.getMessage());
                }
            }

            // Si nada funciona, usar el comando por defecto
            System.out.println("⚠️ Usando comando por defecto");
            return new ProcessBuilder(commands[0]);
        }
    }

    public String getSong(String tiktoklink, String minute, String second) {
        if (!isValidUrl(tiktoklink)) {
            return "❌ Error: URL no permitida. Solo se aceptan enlaces de YouTube, TikTok, Instagram y Facebook.";
        }

        try {
            int idsong = (int) (Math.random() * 10000) + 1;
            String songPath = "./songs/song" + idsong + ".mp3";

            // Crear directorio si no existe
            File songsDir = new File("./songs");
            if (!songsDir.exists()) {
                boolean created = songsDir.mkdirs();
                System.out.println("Directorio songs creado: " + created);
            }

            String startTime = formatTime(minute, second);
            System.out.println("Descargando desde: " + startTime);

            // Obtener el ProcessBuilder correcto
            ProcessBuilder pb = getYtDlpProcessBuilder(startTime, songPath, tiktoklink);
            pb.redirectErrorStream(true);

            System.out.println("Ejecutando comando: " + String.join(" ", pb.command()));

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("yt-dlp: " + line);
                }
            }

            int exitCode = process.waitFor();
            System.out.println("yt-dlp exit code: " + exitCode);

            if (exitCode == 0) {
                // Verificar que el archivo se haya creado
                File songFile = new File(songPath);
                if (songFile.exists() && songFile.length() > 0) {
                    System.out.println("✅ Descarga completada: " + songPath + " (" + songFile.length() + " bytes)");
                    return callAuddApi(songPath);
                } else {
                    System.out.println("❌ Archivo no encontrado o vacío: " + songPath);
                    return "❌ Error: No se pudo descargar el audio";
                }
            } else {
                return "❌ Error al descargar (código: " + exitCode + "). Verifica que la URL sea válida y el video esté disponible.";
            }

        } catch (Exception e) {
            System.err.println("Excepción en getSong: " + e.getMessage());
            e.printStackTrace();

            // Si es el error "No such file or directory", dar mensaje específico
            if (e.getMessage().contains("No such file or directory")) {
                return "❌ Error: yt-dlp no está instalado en el servidor. Verifica la configuración del Dockerfile.";
            }

            return "❌ Error: " + e.getMessage();
        }
    }

    public String getSong(String tiktoklink) {
        return getSong(tiktoklink, "0", "0");
    }
}