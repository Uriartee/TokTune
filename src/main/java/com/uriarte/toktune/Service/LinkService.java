package com.uriarte.toktune.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.stereotype.Service;

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

    // Lista de dominios permitidos
    private static final List<String> ALLOWED_DOMAINS = Arrays.asList(
            "youtube.com",
            "www.youtube.com",
            "youtu.be",
            "m.youtube.com",
            "tiktok.com",
            "www.tiktok.com",
            "vm.tiktok.com",
            "instagram.com",
            "www.instagram.com",
            "facebook.com",
            "www.facebook.com",
            "m.facebook.com",
            "fb.watch"
    );

    /**
     * Valida si la URL pertenece a uno de los sitios permitidos
     * @param urlString URL a validar
     * @return true si es válida, false en caso contrario
     */
    private boolean isValidUrl(String urlString) {
        try {
            // Verificar que la URL no esté vacía o sea null
            if (urlString == null || urlString.trim().isEmpty()) {
                return false;
            }

            // Añadir protocolo si no lo tiene
            if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                urlString = "https://" + urlString;
            }

            URL url = new URL(urlString);
            String host = url.getHost().toLowerCase();

            // Verificar si el dominio está en la lista de permitidos
            return ALLOWED_DOMAINS.contains(host);

        } catch (Exception e) {
            System.out.println("⚠️ URL inválida: " + urlString);
            return false;
        }
    }

    public String filterJson(String resultjson)  throws Exception {

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

        String parsedjson = String.format(
                "Artista: %s\nTítulo: %s\nÁlbum: %s\nFecha de lanzamiento: %s\nLink canción: %s\nApple Music: %s\nSpotify: %s",
                artist, title, album, releaseDate, songLink, appleMusicLink, spotifyLink
        );

        return parsedjson;

    }

    public String callAuddApi(String songPath){
        File songFile = new File(songPath);

        try{
            OkHttpClient client = new OkHttpClient();

            MultipartBody data = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", songFile.getName(), RequestBody.create(songFile, MediaType.parse("audio/mp3")))
                    .addFormDataPart("return", "apple_music,spotify")
                    .addFormDataPart("api_token", auddApiToken).build();

            Request request = new Request.Builder()
                    .url("https://api.audd.io/").post(data).build();

            Response response = client.newCall(request).execute();
            String result = response.body().string();
            System.out.println(result);

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
            // Intentar eliminar el archivo incluso si hay error
            if (songFile.exists()) {
                if (songFile.delete()) {
                    System.out.println("🗑️ Archivo eliminado tras error: " + songPath);
                }
            }
        }
        return "Error";
    }

    // Método para formatear el tiempo en formato HH:MM:SS
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
            // Si hay error, usar valores por defecto
            min = 0;
            sec = 0;
        }

        return String.format("00:%02d:%02d", min, sec);
    }

    public String getSong(String tiktoklink, String minute, String second){
        // Validar la URL antes de procesar
        if (!isValidUrl(tiktoklink)) {
            return "❌ Error: URL no permitida. Solo se aceptan enlaces de YouTube, TikTok, Instagram y Facebook.";
        }

        try{
            String ytDlpCommand = "yt-dlp.exe";
            int idsong = (int) (Math.random() * 10000) + 1;
            String songPath = "./songs/song" + idsong + ".mp3";

            // Formatear el tiempo de inicio
            String startTime = formatTime(minute, second);

            ProcessBuilder pb = new ProcessBuilder(
                    ytDlpCommand,
                    "-x",                           // extraer audio
                    "--audio-format", "mp3",        // formato deseado
                    "--postprocessor-args",
                    "ffmpeg:-ss " + startTime + " -t 10", // comenzar en startTime y durar 10 segundos
                    "-o", songPath,                 // nombre de salida
                    tiktoklink
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("✅ Descarga completada en " + startTime + " por 10 segundos");
                return callAuddApi(songPath);
            } else {
                return "❌ Error al descargar: " + exitCode;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Error";
    }

    // Método de compatibilidad sin parámetros de tiempo (usa valores por defecto)
    public String getSong(String tiktoklink) {
        return getSong(tiktoklink, "0", "0");
    }
}