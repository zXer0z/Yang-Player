# Media3 & FFmpeg keeps
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-dontwarn androidx.media3.decoder.ffmpeg.**

# Media3 Session & Player
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.exoplayer.** { *; }

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# NewPipe's upstream rules: time parsers and Rhino use reflection.
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter { *; }
# Optional desktop JavaBean JSON conversion is not used by the YouTube extractor.
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
# Rhino's JVM bytecode optimizer is unavailable on Android; NewPipe runs interpreted.
-dontwarn jdk.dynalink.**
-keep class com.yang.player.audio.NativeAudio { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
