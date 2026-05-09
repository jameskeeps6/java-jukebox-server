package com.jukebox;

import java.io.IOException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

// Handles connecting to spotify api

public class SpotifyAuthHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        throw new UnsupportedOperationException("Unimplemented method 'handle'");
    }

}