package com.uriarte.toktune.Controller;

import com.uriarte.toktune.Entity.Song;
import com.uriarte.toktune.Service.LinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class LinkController {

    @Autowired
    private LinkService linkService;

    @Value("${app.max-requests-per-day:100}")
    private int maxRequestsPerDay;

    @PostMapping("/postlink")
    public ResponseEntity<?> postLink(@RequestBody Song request, HttpServletRequest httpRequest) {

        // Validación básica
        if (request.getUrl() == null || request.getUrl().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "URL is required"));
        }

        // Validar formato de URL
        if (!isValidUrl(request.getUrl())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid URL format"));
        }

        try {
            // Pasar también los parámetros de tiempo al servicio
            String result = linkService.getSong(
                    request.getUrl(),
                    request.getMinute() != null ? request.getMinute() : "0",
                    request.getSecond() != null ? request.getSecond() : "0"
            );
            return ResponseEntity.ok(Map.of("result", result));
        } catch (Exception e) {
            // Log del error para debugging
            System.err.println("Error processing request from " +
                    httpRequest.getRemoteAddr() + ": " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Service temporarily unavailable"));
        }
    }

    private boolean isValidUrl(String url) {
        try {
            new URL(url);
            return url.startsWith("http://") || url.startsWith("https://");
        } catch (MalformedURLException e) {
            return false;
        }
    }
}