package com.uriarte.toktune.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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
            System.out.println("Respuesta de Audd.io: " + result);

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
                songFile.delete();
            }
            return "Error al procesar el audio";
        }
    }

    /**
     * Descargar audio usando una API externa
     * Opciones: RapidAPI, YouTube API, etc.
     */
    private String downloadAudioFromApi(String videoUrl, String minute, String second) {
        try {
            // Opción 1: Usar RapidAPI YouTube Downloader
            OkHttpClient client = new OkHttpClient();

            // Construir la URL de la API (ejemplo con RapidAPI)
            String apiUrl = "https://youtube-mp3-downloader2.p.rapidapi.com/ytmp3/ytmp3/";

            RequestBody formBody = new FormBody.Builder()
                    .add("url", videoUrl)
                    .build();

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .post(formBody)
                    .addHeader("X-RapidAPI-Key", "TU_RAPIDAPI_KEY") // Necesitas configurar esto
                    .addHeader("X-RapidAPI-Host", "youtube-mp3-downloader2.p.rapidapi.com")
                    .build();

            Response response = client.newCall(request).execute();
            String jsonResponse = response.body().string();

            // Parsear la respuesta para obtener la URL del audio
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);
            String audioUrl = root.path("dlink").asText();

            if (audioUrl != null && !audioUrl.isEmpty()) {
                return downloadAudioFile(audioUrl, minute, second);
            }

            return "Error: No se pudo obtener el audio";

        } catch (Exception e) {
            e.printStackTrace();
            return "Error al descargar el audio";
        }
    }

    private String downloadAudioFile(String audioUrl, String minute, String second) {
        try {
            int idsong = (int) (Math.random() * 10000) + 1;
            String songPath = "./songs/song" + idsong + ".mp3";

            // Crear directorio si no existe
            File songsDir = new File("./songs");
            if (!songsDir.exists()) {
                songsDir.mkdirs();
            }

            // Descargar el archivo de audio
            URL url = new URL(audioUrl);
            try (InputStream in = url.openStream();
                 FileOutputStream out = new FileOutputStream(songPath)) {

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            return songPath;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getSong(String videoUrl, String minute, String second) {
        // Validar la URL antes de procesar
        if (!isValidUrl(videoUrl)) {
            return "❌ Error: URL no permitida. Solo se aceptan enlaces de YouTube, TikTok, Instagram y Facebook.";
        }

        try {
            System.out.println("Procesando URL: " + videoUrl);
            System.out.println("Tiempo de inicio: " + minute + ":" + second);

            // TEMPORAL: Mensaje explicativo mientras se implementa la API externa
            return "⚠️ Servicio temporalmente no disponible. La funcionalidad de descarga de audio está siendo migrada para funcionar en el servidor de producción. Por favor, inténtalo más tarde.";

            // TODO: Descomentar cuando tengas configurada una API externa
            // String audioPath = downloadAudioFromApi(videoUrl, minute, second);
            // if (audioPath != null) {
            //     return callAuddApi(audioPath);
            // } else {
            //     return "❌ Error al descargar el audio";
            // }

        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Error al procesar la solicitud";
        }
    }

    public String getSong(String videoUrl) {
        return getSong(videoUrl, "0", "0");
    }
}