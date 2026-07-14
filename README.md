# java-jukebox-server
A Java HTTP jukebox server enabling shared music playback and collaborative queues, with QR codes for easy song requests.

Enter your client id / client secret and redirect uri in config.txt before running

Have friends connect to server using your ip address

Have them connect to yourIP/port/add to add a song

Main player to display on tv is yourIP/port/index.html

To access admin panel to control playback yourIP/port/adminPanel
yourIP/port/adminPanel

Server.java
    starts everything

Handlers.java
    handles browser requests

SpotifyAuthenticator.java
    login/token

SpotifyPlayer.java
    play/pause/skip

Song.java
    song data

QRCodeGenerator.java
    QR creation 

