package com.jukebox;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;

import java.nio.charset.StandardCharsets;

// This class is responsible for controlling playback on Spotify.
// It provides a method to play a specific track on the user's Spotify account using the Spotify Web
public class SpotifyPlayer {

    public static String deviceId;

    // This method sends a request to the Spotify Web API to play a specific track
    // on the user's account.
    // It requires the track URI and a valid access token for authentication.
    public static boolean play(String trackUri, String token) {

        // If the token is null or empty, return false as we cannot make a request
        // without a valid token
        if (token == null || token.isEmpty())
            return false;

        try {

            String url = "https://api.spotify.com/v1/me/player/play";

            if (deviceId != null) {

                url += "?device_id=" +
                        URLEncoder.encode(
                                deviceId,
                                StandardCharsets.UTF_8);
            }

            // Prepare the JSON body for the request, specifying the track URI to play
            String body = "{\"uris\":[\"" + trackUri + "\"]}";

            // Create an HTTP request to the Spotify API with the appropriate headers and
            // body
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header(
                            "Authorization",
                            "Bearer " + token)
                    .header(
                            "Content-Type",
                            "application/json")
                    .PUT(
                            HttpRequest.BodyPublishers.ofString(body))
                    .build();

            // Send the request and get the response from the Spotify API
            HttpResponse<String> response = HttpClient.newHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 204;

        } catch (Exception e) {

            e.printStackTrace();
            return false;
        }
    }
}