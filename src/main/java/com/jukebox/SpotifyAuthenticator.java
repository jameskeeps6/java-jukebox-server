
package com.jukebox;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;

import com.sun.net.httpserver.*;

// This class handles Spotify authentication by implementing the HttpHandler interface. 
// It manages the OAuth flow, exchanges authorization codes for access tokens, and provides a public getter for the access token.
public class SpotifyAuthenticator implements HttpHandler {

    private static String CLIENT_ID;
    private static String CLIENT_SECRET;
    private static String REDIRECT_URI; // Marked as static so it is shared across all routing instances safely
    private static String oauthState = java.util.UUID.randomUUID().toString();

    String refreshToken;
    private static volatile String accessToken;

    // Public getter to safely return the access token to other parts of your app
    public static String getAccessToken() {
        return accessToken;
    }

    // Load client ID, secret, and redirect URI from a config file
    public static class ConfigLoader {

        public static void loadConfig(String configFileName) {

            System.out.println("Trying to load config....");

            // Fallback safety if empty or null is passed
            if (configFileName == null || configFileName.trim().isEmpty()) {
                configFileName = "config.txt";
            }
            InputStream input = null;

            // Attempt to load the config file from the classpath or root directory
            try {
                input = ConfigLoader.class.getClassLoader().getResourceAsStream(configFileName);

                // If not found in the classpath, try loading from the root directory
                if (input == null) {
                    java.io.File rootDirectoryFile = new java.io.File(configFileName);
                    if (rootDirectoryFile.exists()) {
                        input = new java.io.FileInputStream(rootDirectoryFile);
                    }
                }

                // If still not found, print error and exit
                if (input == null) {
                    System.err.println("CRITICAL CONFIG ERROR: Could not locate "
                            + configFileName
                            + " in build resource package or root directory contexts.");
                    return;
                }

                // Read the config file line by line and assign values to CLIENT_ID,
                // CLIENT_SECRET, and REDIRECT_URI
                try (BufferedReader reader = new BufferedReader(

                        // Use UTF-8 encoding to read the config file
                        new InputStreamReader(input, StandardCharsets.UTF_8))) {

                    // Read all lines from the config file into a list
                    List<String> lines = reader.lines().collect(Collectors.toList());

                    for (String line : lines) {
                        line = line.trim();

                        if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                            continue;
                        }

                        String[] parts = line.split("=", 2);

                        String key = parts[0].trim();
                        String value = parts[1].trim().replace("\r", "").replace("\n", "");

                        if (key.equalsIgnoreCase("id") || key.equalsIgnoreCase("client_id")) {
                            CLIENT_ID = value;
                            System.out.println("Client ID assigned.");
                        } else if (key.equalsIgnoreCase("client_secret")) {
                            CLIENT_SECRET = value;
                            System.out.println("Client Secret assigned.");
                        } else if (key.equalsIgnoreCase("redirect_uri")) {
                            REDIRECT_URI = value;
                            System.out.println("Redirect URI assigned.");
                        }
                    }

                    System.out.println("Config loaded and assigned successfully.");

                }

            } catch (Exception e) {
                System.err.println("Error reading config resource:");
                e.printStackTrace();

            } finally {
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    /*
     * This method handles the HTTP requests for Spotify authentication.
     * / It checks if the request contains an authorization code, exchanges it for
     * an access token
     * and redirects the user accordingly.
     */

    public void handle(HttpExchange exchange) throws IOException {
        // Get the host and port from the request headers to construct the redirect URI
        String host = exchange.getRequestHeaders().getFirst("Host");
        String port = "8000";

        // If the host header contains a port, extract it
        if (host != null && host.contains(":")) {
            String[] hostParts = host.split(":");

            if (hostParts.length > 1) {
                port = hostParts[1];
            }
        }

        // Use the exact redirect URI loaded from the config file instead of dynamically
        // guessing it
        String dynamicRedirectUri = (REDIRECT_URI != null && !REDIRECT_URI.isEmpty()) ? REDIRECT_URI
                : "http://localhost:" + port + "/spotify/callback";

        // Update the REDIRECT_URI to the dynamic one for this session
        URI requestURI = exchange.getRequestURI();
        // Get the query parameters from the request URI
        String query = requestURI.getQuery();

        // Check if the query contains the authorization code
        if (query != null && query.contains("code=")) {
            System.out.println("Callback received! Parsing auth code...");

            // Initialize variables to hold the authorization code and state
            String code = "";
            String state = "";

            // Parse the query parameters to extract the authorization code and state
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);

                // Check if the parameter has both a key and a value
                if (pair.length == 2) {
                    if (pair[0].equals("code"))
                        code = pair[1];

                    if (pair[0].equals("state"))
                        state = pair[1];
                }
            }

            // Check if the state parameter matches the expected OAuth state to prevent CSRF
            // attacks
            if (!state.equals(oauthState)) {

                System.out.println("OAuth state mismatch - rejecting request");

                String errorResponse = "OAuth state mismatch";

                exchange.sendResponseHeaders(403, errorResponse.length());
                exchange.getResponseBody().write(errorResponse.getBytes());
                exchange.close();

                return;
            }

            // If the state matches, proceed to exchange the authorization code for an
            // access token
            System.out.println("Success! Authorization code retrieved");

            // Exchange the authorization code for an access token
            String jsonResponse = exchangeCodeForToken(code, dynamicRedirectUri);

            // Extract the access token from the JSON response and store it
            accessToken = extractAccessToken(jsonResponse);

            // Log the result of the access token extraction
            if (accessToken == null || accessToken.isEmpty()) {
                System.out.println("Token failed to parse");
            } else {
                System.out.println("Auth code received (hidden)");
            }

            // Log the successful retrieval of the access token
            System.out.println("LOGIN SUCCESS - ACCESS TOKEN RECEIVED");

            // Redirect the user to the admin panel after successful login
            String redirectUrl = "http://" + (host != null ? host : "127.0.0.1:" + port) + "/adminPanel";

            // Redirect user back to admin panel after successful login
            exchange.getResponseHeaders().add("Location", redirectUrl);
            exchange.sendResponseHeaders(303, -1);
            exchange.close();

            return;
        }

        // If the request does not contain an authorization code, initiate the Spotify
        // login process
        String scope = String.join(" ",
                "streaming",
                "user-read-email",
                "user-read-private",
                "user-modify-playback-state",
                "user-read-playback-state");

        // Construct the Spotify authorization URL with the necessary parameters
        String url = "https://accounts.spotify.com/authorize"
                + "?response_type=code"
                + "&client_id=" + CLIENT_ID
                + "&redirect_uri=" + java.net.URLEncoder.encode(dynamicRedirectUri, StandardCharsets.UTF_8)
                + "&scope=" + java.net.URLEncoder.encode(scope, StandardCharsets.UTF_8)
                + "&state=" + oauthState;

        // Log the redirection to the Spotify login page
        System.out.println("Sending user to spotify login page...");

        // Redirect the user to the Spotify login page
        exchange.getResponseHeaders().add("Location", url);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    /*
     * Exchanges the Spotify authorization code for an access token by sending a
     * request to Spotify's API.
     * Returns the response JSON
     */

    private String exchangeCodeForToken(String authCode, String dynamicRedirectUri) {
        // Prepare the request body for the token exchange
        try {
            // Construct the request body with the necessary parameters
            String requestBody = "grant_type=authorization_code"
                    + "&code=" + java.net.URLEncoder.encode(authCode, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + java.net.URLEncoder.encode(dynamicRedirectUri, StandardCharsets.UTF_8);
            // Prepare the credentials for Basic Authentication
            String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
            // Encode the credentials in Base64 format
            String base64Credentials = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            // Create an HTTP client to send the request
            HttpClient client = HttpClient.newHttpClient();
            // Build the HTTP request to exchange the authorization code for an access token
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://accounts.spotify.com/api/token"))
                    .header("Authorization", "Basic " + base64Credentials)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            // Send the request and get the response
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            // Return the response body containing the access token in JSON format
            return response.body();

            // Log the response for debugging purposes
        } catch (Exception e) {
            System.err.println("An error occurred during token exchange: " + e.getMessage());
            return "";
        }
    }

    // Extracts the access token from the JSON response returned by Spotify's API
    // after exchanging the authorization code.
    private String extractAccessToken(String json) {
        // Check if the JSON response is null or does not contain the "access_token"
        // field
        if (json == null || !json.contains("\"access_token\"")) {
            return "";
        }
        // Attempt to parse the JSON response and extract the access token
        try {
            // Split the JSON response to isolate the access token
            String[] parts = json.split("\"access_token\":\"");
            // Check if the split resulted in more than one part, indicating the presence of
            // the access token
            if (parts.length > 1) {
                // Further split the second part to isolate the access token value
                String[] subParts = parts[1].split("\"");
                // Check if the subParts array has at least one element, which should be the
                // access token
                if (subParts.length > 0) {
                    // Return the extracted access token
                    return subParts[0];
                }
            }
            // If the access token could not be extracted return error and empty string
        } catch (Exception e) {

            System.err.println("Failed parsing access token: " + e.getMessage());
        }
        return "";
    }
}