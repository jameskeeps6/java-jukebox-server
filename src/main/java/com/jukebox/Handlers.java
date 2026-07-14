package com.jukebox;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

// This class contains all the HTTP handlers for the Jukebox server. Each handler corresponds 
// to a specific endpoint and handles the request and response logic for that endpoint.
public class Handlers {

    // When user visits /qr, send them the QR code image
    static class QRHandler implements HttpHandler {

        public void handle(HttpExchange exchange) throws IOException {

            // Read the QR code image file
            byte[] bytes = Files.readAllBytes(java.nio.file.Path.of("QRCode.png"));

            // Set the content type to image/png and send the response
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, bytes.length);

            // Send the image data to the browser
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // Load admin panel html page
    static class AdminPanel implements HttpHandler {

        public void handle(HttpExchange exchange) throws IOException {

            // Read the html file
            InputStream is = Server.class.getClassLoader().getResourceAsStream("adminPanel.html");

            // If the file is not found, send a 404 response
            if (is == null) {
                System.out.println("adminPanel.html NOT FOUND");
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            // Read all bytes from the input stream
            byte[] bytes = is.readAllBytes();

            // Set the content type to text/html and send the response
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }

    }

    // When user visits main page "/", send them the main webpage html
    static class MainPageHandler implements HttpHandler {

        // Handle method runs everytime someone visits
        public void handle(HttpExchange exchange) throws IOException {

            // Read the html file
            InputStream is = Server.class.getClassLoader().getResourceAsStream("index.html");

            // If the file is not found, send a 404 response
            if (is == null) {
                System.out.println("index.html NOT FOUND");
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            // Read all bytes from the input stream
            byte[] bytes = is.readAllBytes();

            // Store html data in the response string and send it to the browser
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    // Saves the Spotify Web Playback SDK device ID
    static class DeviceHandler implements HttpHandler {

        public void handle(HttpExchange exchange) throws IOException {

            String query = exchange.getRequestURI().getQuery();

            if (query != null && query.startsWith("id=")) {

                Server.spotifyDeviceId = URLDecoder.decode(
                        query.substring(3),
                        StandardCharsets.UTF_8);
                System.out.println(
                        "Spotify Device Connected: "
                                + Server.spotifyDeviceId);
            }

            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        }
    }

    // Returns current Spotify playing song
    static class NowPlayingHandler implements HttpHandler {

        public void handle(HttpExchange exchange) throws IOException {

            String token = SpotifyAuthenticator.getAccessToken();

            if (token == null || token.isEmpty()) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try {

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.spotify.com/v1/me/player"))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build();

                HttpClient client = HttpClient.newHttpClient();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                ObjectMapper mapper = new ObjectMapper();

                JsonNode root = mapper.readTree(response.body());

                JsonNode item = root.path("item");

                if (item.isMissingNode()) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                String name = item.path("name").asText();

                String artist = item.path("artists")
                        .path(0)
                        .path("name")
                        .asText();

                String image = item.path("album")
                        .path("images")
                        .path(0)
                        .path("url")
                        .asText();

                String json = "{"
                        + "\"name\":\"" + name + "\","
                        + "\"artist\":\"" + artist + "\","
                        + "\"image\":\"" + image + "\""
                        + "}";

                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders()
                        .set("Content-Type", "application/json");

                exchange.sendResponseHeaders(200, bytes.length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }

            } catch (Exception e) {

                e.printStackTrace();

                exchange.sendResponseHeaders(500, -1);
            }
        }
    }

    // When user visits /add?song=SONGNAME, search Spotify for the song and add it
    // to the queue
    static class AddSongHandler implements HttpHandler {

        public void handle(HttpExchange exchange) throws IOException {

            String query = exchange.getRequestURI().getQuery();
            String song = "";

            if (query != null && query.startsWith("song=")) {
                song = query.substring(5);
            }

            if (!song.isEmpty()) {

                try {

                    Song songObj = Server.searchSpotifyTrack(
                            song,
                            SpotifyAuthenticator.getAccessToken());

                    if (songObj != null) {

                        Server.songs.add(songObj);

                        System.out.println(
                                "Added: "
                                        + songObj.name
                                        + " - "
                                        + songObj.artist);

                        System.out.println(
                                "Queue size: "
                                        + Server.songs.size());

                    } else {
                        System.out.println("Song not found");
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Redirect user back after adding song
            exchange.getResponseHeaders().add("Location", "/add");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        }
    }

    static class PauseHandler implements HttpHandler {

        public void handle(HttpExchange exchange) throws IOException {

            String token = SpotifyAuthenticator.getAccessToken();
            String responseMessage = "Paused";

            try {

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(
                                "https://api.spotify.com/v1/me/player/pause"))
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .PUT(
                                HttpRequest.BodyPublishers.noBody())
                        .build();

                HttpClient client = HttpClient.newHttpClient();

                HttpResponse<String> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString());

                System.out.println(
                        "Spotify pause response: "
                                + response.statusCode());

                if (response.statusCode() != 204) {
                    responseMessage = "Pause failed: "
                            + response.statusCode()
                            + " "
                            + response.body();
                }

            } catch (Exception e) {
                e.printStackTrace();
                responseMessage = "Pause failed";
            }

            byte[] bytes = responseMessage.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders()
                    .set("Content-Type", "text/plain");

            exchange.sendResponseHeaders(
                    200,
                    bytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static class ResumeHandler implements HttpHandler {

        public void handle(HttpExchange exchange) throws IOException {

            String token = SpotifyAuthenticator.getAccessToken();
            String responseMessage = "Playing";

            try {

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(
                                "https://api.spotify.com/v1/me/player/play"))
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .PUT(
                                HttpRequest.BodyPublishers.noBody())
                        .build();

                HttpClient client = HttpClient.newHttpClient();

                HttpResponse<String> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString());

                System.out.println(
                        "Spotify resume response: "
                                + response.statusCode());

                if (response.statusCode() != 204) {
                    responseMessage = "Resume failed: "
                            + response.statusCode()
                            + " "
                            + response.body();
                }

            } catch (Exception e) {

                e.printStackTrace();
                responseMessage = "Resume failed";
            }

            byte[] bytes = responseMessage.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders()
                    .set("Content-Type", "text/plain");

            exchange.sendResponseHeaders(
                    200,
                    bytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // When user visits /songs, send the list of songs in the queue as JSON
    static class SongsHandler implements HttpHandler {

        public void handle(HttpExchange exchange) throws IOException {

            // Create a JSON array of songs in the queue
            StringBuilder json = new StringBuilder();
            json.append("[");

            // Loop through the songs and add them to the JSON array
            for (int i = 0; i < Server.songs.size(); i++) {
                Song s = Server.songs.get(i);

                // Append each song as a JSON object
                json.append("{")
                        .append("\"uri\":\"").append(s.uri).append("\",")
                        .append("\"name\":\"").append(s.name).append("\",")
                        .append("\"artist\":\"").append(s.artist).append("\",")
                        .append("\"image\":\"").append(s.image).append("\"")
                        .append("}");

                // If not the last song, add a comma to separate JSON objects
                if (i < Server.songs.size() - 1)
                    json.append(",");
            }

            // Close the JSON array
            json.append("]");

            // Convert the JSON string to bytes
            byte[] bytes = json.toString().getBytes();

            // Set the content type to application/json and send the response
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // When user visits /playNext, send them the next song in the queue and remove
    // it from the queue
    static class PlayNextHandler implements HttpHandler {

        public void handle(HttpExchange exchange) throws IOException {
            String responseMessage = "Queue is empty";
            int statusCode = 200;

            // 1. Check if we have songs in our list
            if (!Server.songs.isEmpty()) {
                Song nextSong = Server.songs.get(0);

                // 2. Process player logic by looking up the browser device ID dynamically
                boolean apiSuccess = Server.playOnSpotify(
                        nextSong.uri,
                        SpotifyAuthenticator.getAccessToken());
                if (apiSuccess) {
                    // 3. Only remove from queue if Spotify successfully executed it
                    Server.songs.remove(0);
                    responseMessage = "Now playing: " + nextSong.name;
                    System.out.println("[SUCCESS] Playing: " + nextSong.name);
                } else {
                    // If it fails, keep the song safe in the queue
                    statusCode = 500;
                    responseMessage = "Spotify failed to play. Ensure your Jukebox Index Page is open and active!";
                    System.out.println("[ERROR] Spotify rejected play command. Keeping song in queue.");
                }
            }

            // Send clean text response back to the browser
            byte[] responseBytes = responseMessage.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    // When user visits /queue, send them the queue webpage html
    static class QueueHandler implements HttpHandler {

        public void handle(HttpExchange exchange) throws IOException {

            // Read the html file
            InputStream is = Server.class.getClassLoader().getResourceAsStream("queue.html");

            // If the file is not found, send a 404 response
            if (is == null) {
                System.out.println("queue.html NOT FOUND");
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            // Read all bytes from the input stream
            byte[] bytes = is.readAllBytes();

            // Set the content type to text/html and send the response
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }

    }

    // Skip currently playing Spotify song
    static class SkipHandler implements HttpHandler {

        public void handle(HttpExchange exchange) throws IOException {

            System.out.println("[BACKEND DEBUG] Skip request received.");

            String token = SpotifyAuthenticator.getAccessToken();
            String responseMessage;

            try {

                if (token == null || token.trim().isEmpty()) {

                    responseMessage = "No Spotify token";

                } else {

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "https://api.spotify.com/v1/me/player/next"))
                            .header(
                                    "Authorization",
                                    "Bearer " + token.trim())
                            .POST(
                                    HttpRequest.BodyPublishers.noBody())
                            .build();

                    HttpClient client = HttpClient.newHttpClient();

                    HttpResponse<String> response = client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString());

                    System.out.println(
                            "[SPOTIFY SKIP STATUS] "
                                    + response.statusCode());

                    System.out.println(
                            "[SPOTIFY SKIP BODY] "
                                    + response.body());

                    if (response.statusCode() == 204
                            || response.statusCode() == 200) {

                        if (!Server.songs.isEmpty()) {

                            Song nextSong = Server.songs.remove(0);

                            boolean success = Server.playOnSpotify(
                                    nextSong.uri,
                                    SpotifyAuthenticator.getAccessToken());

                            if (success) {
                                responseMessage = "Skipped and playing: " + nextSong.name;
                            } else {
                                Server.songs.add(0, nextSong);
                                responseMessage = "Skipped, but failed to start next queued song.";
                            }

                        } else {

                            responseMessage = "Skipped. Queue is empty.";

                        }
                    } else {

                        responseMessage = "Skip failed: "
                                + response.statusCode()
                                + " "
                                + response.body();
                    }
                }

            } catch (Exception e) {

                e.printStackTrace();

                responseMessage = "Skip failed";

            }

            byte[] bytes = responseMessage.getBytes(StandardCharsets.UTF_8);

            int statusCode = responseMessage.equals("Skip successful")
                    ? 200
                    : 500;

            exchange.getResponseHeaders()
                    .set(
                            "Content-Type",
                            "text/plain");

            exchange.sendResponseHeaders(
                    statusCode,
                    bytes.length);

            try (OutputStream os = exchange.getResponseBody()) {

                os.write(bytes);

            }
        }
    }

    // When user visits /token, send them the current Spotify access token as plain
    // text
    static class TokenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("[BACKEND DEBUG] Frontend requested the Spotify access token via /token");

            // Get the current Spotify access token from the SpotifyAuthHandler
            String currentToken = SpotifyAuthenticator.getAccessToken();

            boolean hasToken = currentToken != null && !currentToken.trim().isEmpty();

            // If no token exists, log a warning and send a placeholder response
            if (!hasToken) {
                System.out.println(
                        "[BACKEND WARNING] /token requested but no token exists. User needs to login");
                currentToken = "NO_TOKEN_AVAILABLE";
            }

            // Convert the token to bytes for sending in the response
            byte[] responseBytes = currentToken.trim().getBytes(StandardCharsets.UTF_8);

            // Set the content type to text/plain and send the response
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }

            // Only print SUCCESS if a real token exists
            if (hasToken) {
                System.out.println("[BACKEND SUCCESS] Token successfully dispatched to frontend.");
            }
        }
    }

    // Create add page html
    static class AddPageHandler implements HttpHandler {

        public void handle(HttpExchange exchange) throws IOException {

            InputStream is = Server.class.getClassLoader().getResourceAsStream("addSong.html");

            if (is == null) {
                System.out.println("add.html NOT FOUND");
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            byte[] bytes = is.readAllBytes();

            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, bytes.length);

            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
}
