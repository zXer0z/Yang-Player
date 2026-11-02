package com.yang.player.online;

import android.net.Uri;

/** Adapted transport model for the existing YouTube Music / NewPipe integration. */
final class OnlineTrack {
    final Uri uri;
    final String title, artist, album, name;
    final long duration;
    OnlineTrack(Uri uri, String title, String artist, String album, String name, long duration) {
        this.uri=uri; this.title=title; this.artist=artist; this.album=album; this.name=name; this.duration=duration;
    }
}
