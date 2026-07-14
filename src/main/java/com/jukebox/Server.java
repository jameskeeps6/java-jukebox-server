package com.jukebox;

import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jukebox.Handlers.*;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

// Main Server Class
public class Server {

    // Create list to store songs in the queue
    public static List<Song> songs = new ArrayList<>();
    public static String spotifyDeviceId = null; // Store the Spotify device ID for playback

    // Main method
    public static void main(String[] args) throws Exception {

        // Grab the local IP address of the computer to display in the console and
        // generate the QR code
        String computerIP = getLocalIpAddress();

        // Load Spotify API credentials from the config file
        SpotifyAuthenticator.ConfigLoader.loadConfig("config.txt");

        // Create the HTTP server
        HttpServer server = createServer();

        // Register the routes for handling different HTTP requests
        registerRoutes(server);

        // Generate the QR code for the /add page using the local IP address
        generateQRCode(computerIP);

        // Start the server to listen for incoming requests
        server.start();
        System.out.println("Server started on http://" + computerIP + ":8000");
    }

    // Create web pages
    static void registerRoutes(HttpServer server) {

        server.createContext("/", new MainPageHandler());
        server.createContext("/add", new AddPageHandler());
        server.createContext("/adminPanel", new AdminPanel());

        server.createContext("/submitAdd", new AddSongHandler());
        server.createContext("/queue", new QueueHandler());
        server.createContext("/qr", new QRHandler());
        server.createContext("/songs", new SongsHandler());
        server.createContext("/playNext", new PlayNextHandler());
        server.createContext("/token", new TokenHandler());

        server.createContext("/spotify/callback", new SpotifyAuthenticator());
        server.createContext("/spotify/login", new SpotifyAuthenticator());
        server.createContext("/device", new DeviceHandler());
        server.createContext("/skip", new SkipHandler());
        server.createContext("/pause", new PauseHandler());
        server.createContext("/resume", new ResumeHandler());
        server.createContext("/now-playing", new NowPlayingHandler());
        server.setExecutor(null);
    }

    // Create server
    static HttpServer createServer() throws Exception {
        return HttpServer.create(new InetSocketAddress("0.0.0.0", 8000), 0);
    }

    // Grab IP of host computer
    static String getLocalIpAddress() throws Exception {
        for (NetworkInterface ni : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
            for (InetAddress addr : java.util.Collections.list(ni.getInetAddresses())) {
                if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                    return addr.getHostAddress();
                }
            }
        }
        return "127.0.0.1";
    }

    // Generate QR code
    static void generateQRCode(String ip) {
        try {
            QRCodeGenerator.generate("http://" + ip + ":8000/add", "QRCode.png");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Standard Java HTTP request handling context targeting your account endpoints
    public static boolean playOnSpotify(String trackUri, String token) {
        if (token == null || token.trim().isEmpty())
            return false;

        try {
            HttpClient client = HttpClient.newHttpClient();
            String targetUrl = "https://api.spotify.com/v1/me/player/play";

            // Match the clean device query structure
            if (spotifyDeviceId != null && !spotifyDeviceId.isEmpty()) {
                targetUrl += "?device_id=" + URLEncoder.encode(spotifyDeviceId.trim(), StandardCharsets.UTF_8);
            }

            String jsonBody = "{\"uris\": [\"" + trackUri + "\"]}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Authorization", "Bearer " + token.trim())
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[SPOTIFY BACKEND] Play response code: " + response.statusCode());
            System.out.println("[SPOTIFY BODY] " + response.body());

            return response.statusCode() == 204;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Searches Spotify for a track by name and returns a Song object with the
    // track's URI, name, and artist
    public static Song searchSpotifyTrack(String songName, String token) throws Exception {

        // Validate input parameters
        if (songName == null || songName.trim().isEmpty()) {
            System.out.println("Error: Song name parameter is empty.");
            return null;
        }

        // Validate the Spotify access token
        if (token == null || token.trim().isEmpty()) {
            System.out.println("Error: Spotify token is missing.");
            return null;
        }

        // Create an HTTP client for making requests to Spotify's API
        HttpClient client = HttpClient.newHttpClient();

        // Encode the song name for use in a URL query parameter
        String encodedQuery = URLEncoder.encode(songName.trim(), StandardCharsets.UTF_8);

        // Construct the Spotify search API URL with the encoded query, specifying that
        // we want to search for tracks and limit the results to 1
        String webAddress = "https://api.spotify.com/v1/search?q="
                + encodedQuery
                + "&type=track&limit=1";
        System.out.println("THE RUNNING URL IS: " + webAddress);

        // Build the HTTP GET request to Spotify's search API, including the
        // authorization header with the Bearer token
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webAddress))
                .header("Authorization", "Bearer " + token.trim())
                .GET()
                .build();

        // Send the request and get the response from Spotify's API
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Check if the response status code indicates success (200 OK). If not, log the
        // response body for debugging and return null to indicate that the search
        // failed.
        if (response.statusCode() != 200) {
            System.out.println("Response Body: " + response.body());
            return null;
        }

        // Parse the JSON response from Spotify's API to extract the track information
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());
        JsonNode items = root.path("tracks").path("items");

        // If no tracks are found, log a message and return null
        if (items.isEmpty()) {
            System.out.println("No tracks found for query: " + songName);
            return null;
        }

        // Extract the first track from the search results and retrieve its URI, name,
        // and artist information
        JsonNode firstTrack = items.get(0);
        String uri = firstTrack.path("uri").asText();
        String name = firstTrack.path("name").asText();
        String artist = firstTrack.path("artists").path(0).path("name").asText();
        String image = firstTrack.path("album").path("images").get(0).path("url").asText();
        // Return a new Song object containing the extracted information
        return new Song(uri, name, artist, image);
    }

    // Returns simple login state (NO TOKEN LEAK / NO SIDE EFFECTS)
    static class AuthStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            boolean loggedIn = SpotifyAuthenticator.getAccessToken() != null
                    && !SpotifyAuthenticator.getAccessToken().trim().isEmpty();

            String response = loggedIn ? "true" : "false";

            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

}
