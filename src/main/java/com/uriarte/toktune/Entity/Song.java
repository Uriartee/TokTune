package com.uriarte.toktune.Entity;

public class Song {
    private String url;
    private String minute;
    private String second;

    // Constructor vacío
    public Song() {}

    // Constructor con parámetros
    public Song(String url, String minute, String second) {
        this.url = url;
        this.minute = minute;
        this.second = second;
    }

    // Getters y Setters
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMinute() {
        return minute;
    }

    public void setMinute(String minute) {
        this.minute = minute;
    }

    public String getSecond() {
        return second;
    }

    public void setSecond(String second) {
        this.second = second;
    }
}