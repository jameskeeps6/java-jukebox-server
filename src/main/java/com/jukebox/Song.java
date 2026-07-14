package com.jukebox;

// This class represents a song with its URI, name, artist, and image. 
// It is used to store and manage song information in the Jukebox application.
public class Song {

    // The URI of the song, which is used to identify and play the song on Spotify.
    public String uri;
    public String name;
    public String artist;
    public String image;

    // Constructor to initialize a Song object with its URI, name, artist, and
    // image.
    public Song(String uri, String name, String artist, String image) {
        this.uri = uri;
        this.name = name;
        this.artist = artist;
        this.image = image;
    }
}