package com.jukebox;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

// 

// Class
public class DaServer {
    // Create list to store the que of songs
    static List<String> songs = new ArrayList<>();
    // Current song tracker
    static String currentSong = "";

    // Main method
    public static void main(String[] args) throws Exception {
        System.out.println("Server starting on http://localhost:8000");
        // Create the server object (Pages on the site)
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        // When someone visits "/ " on the site than run MyHandler to decide what to
        // send back
        // When at specific url than use that assigned handler to handle it
        // Routing
        server.createContext("/", new MyHandler());
        server.createContext("/add", new AddSongHandler());
        server.createContext("/queue", new QueueHandler());
        server.createContext("/qr", new QRHandler());
        // Use default system threading
        server.setExecutor(null);
        // Create qr Code
        try {
            QRCodeGenerator.generate("http://localhost:8000/queue", "QRCode.png");

        } catch (Exception e) {
            e.printStackTrace();
        }
        // Start the server
        server.start();
        // Start Backround worker , always runs in Backround
        // Checks current song and switches to next song after 15 seconds
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        // wait 15 seconds (simulate song playing)
                        Thread.sleep(15000);
                        // move to next song
                        playNextSong();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        // start the background thread
        worker.start();
    }

    // Methods
    static void playNextSong() {
        if (!songs.isEmpty()) {
            currentSong = songs.remove(0);
        } else {
            currentSong = "";
        }
    }
    // All handlers

    // QR Code Generator handler
    static class QRHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            byte[] bytes = Files.readAllBytes(Paths.get("QRCode.png"));

            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, bytes.length);

            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    // This handler class responds to the web requests
    static class MyHandler implements HttpHandler {
        // Handle method runs everytime someone visits
        public void handle(HttpExchange exchange) throws IOException {
            // Read the html file
            byte[] bytes = Files.readAllBytes(Paths.get("TouchTunesJava/src/addSong.html"));
            // Store html data in the response string
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, bytes.length);
            // Send data to browser
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
            // End
        }

    }

    /*
     * ADD SONG handler
     * User says: /add?song=hello
     * Java does:
     * 1. read "hello"
     * 2. put in list
     * 3. say "got it"
     */
    static class AddSongHandler implements HttpHandler {
        // Grabs song from the url
        public void handle(HttpExchange exchange) throws IOException {
            // Grab url
            String query = exchange.getRequestURI().getQuery();
            String song = "";
            // Remove song= and leave plain text
            if (query != null && query.startsWith("song=")) {
                song = query.substring(5);
            }
            // Add song to list
            if (!song.isEmpty()) {
                songs.add(song);
            }
            // Send data back to browser
            String response = "Added song: " + song + "\nTotal songs: " + songs.size();
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, response.length());

            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    // Que page handler displays que only
    // Shows currentSong, show songs, and renders html
    // Also builds html page for que in java not a html file
    static class QueueHandler implements HttpHandler {

        public void handle(HttpExchange exchange) throws IOException {
            // Used to build HTML page step-by-step
            StringBuilder html = new StringBuilder();
            // Start HTML document
            html.append("<html><head>");
            // Auto-refresh page every 5 seconds (updates queue automatically)
            html.append("<meta http-equiv='refresh' content='5'>");
            // Page title shown in browser tab
            html.append("<title>Jukebox Queue</title>");
            // End of header section
            html.append(
                    "</head><body style='background-color:black; color:white; font-family:Arial; text-align:center;'>");
            html.append("<h1 style='font-size:50px;'>Jukebox Queue</h1>"); // Show currently playing song
            html.append("<h2 style='font-size:40px; color:#00ffcc;'>Now Playing: "); // If nothing is playing
            if (currentSong.isEmpty()) {
                html.append("Nothing");
            } else {
                // Show current song
                html.append("<span style='color:#ffcc00;'>" + currentSong + "</span>");
            }
            html.append("</h2>");
            html.append("<h3>Scan to Add Songs</h3>");
            html.append("<img src='/qr' width='250' height='250'/>");
            // Section title for upcoming songs
            html.append("<h3>Up Next</h3>");
            // If queue is empty
            if (songs.isEmpty()) {
                html.append("<p>No songs in queue</p>");
            } else {
                // Start ordered list
                // Loop thru songs in que
                html.append("<ol style='display:inline-block; text-align:left; font-size:25px;'>");
                for (String song : songs) {
                    // Add each song as a list item
                    html.append("<li>").append(song).append("</li>");
                }
                // End list
                html.append("</ol>");
            }
            // End HTML page
            html.append("</body></html>");
            // Convert HTML builder into a string response
            String response = html.toString();
            // Tell browser we are sending HTML
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            // Send HTTP OK response + size of content
            exchange.sendResponseHeaders(200, response.getBytes().length);
            // Send actual HTML to browser
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            // Close connection
            os.close();
        }
    }

}