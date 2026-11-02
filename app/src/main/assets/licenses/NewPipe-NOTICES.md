# Online audio dependencies

YANG Player integrates NewPipeExtractor v0.26.5 without modifications for YouTube search and audio extraction. The account/catalog adapter in com.yang.player.online was adapted from the user's MP3_player project.

NewPipeExtractor is GPL-3.0-or-later; see NewPipe-GPL-3.0.txt. The combined application is distributed under GPL-3.0-or-later. Dependencies retain their licenses. NewPipe is not an official YouTube API.

- NewPipeExtractor v0.26.5: https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5 (GPL-3.0-or-later).
- nanojson: https://github.com/TeamNewPipe/nanojson/tree/e9d656ddb49a412a5a0a5d5ef20ca7ef09549996 (Apache-2.0).
- jsoup 1.22.2: https://github.com/jhy/jsoup (MIT).
- Rhino 1.8.1: https://github.com/mozilla/rhino/tree/Rhino1_8_1_Release (MPL-2.0; additional notices in JAR).
- protobuf-javalite 4.35.1: https://github.com/protocolbuffers/protobuf (BSD-3-Clause).
- jsr305 3.0.2: https://github.com/findbugsproject/findbugs (BSD).

The desktop Rhino JSR-223 engine adapter is excluded from the Android build. Metadata caches store stable video identities, not expiring playback URLs or session credentials. Account credentials are encrypted with Android Keystore.
