package com.yang.player.online;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.util.*;
import org.json.*;

/** Private account-scoped catalog cache: never stores cookies or expiring playback URLs. */
final class OnlineHomeCache {
    static final class Snapshot{
        final String scope;final long created;final List<YouTubeMusicClient.PlaylistItem> playlists;final List<YouTubeMusicClient.HomeSection> sections;
        Snapshot(String scope,long created,List<YouTubeMusicClient.PlaylistItem> playlists,List<YouTubeMusicClient.HomeSection> sections){this.scope=scope;this.created=created;this.playlists=new ArrayList<>(playlists);this.sections=new ArrayList<>(sections);}
    }
    private static volatile Snapshot memory;
    static String scope(Context c){try{String cookie=YouTubeAuth.isLoggedIn(c)?YouTubeAuth.getCookie(c):null;byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest((cookie==null?"guest":cookie).getBytes("UTF-8"));StringBuilder key=new StringBuilder();for(byte b:digest)key.append(String.format(Locale.ROOT,"%02x",b&255));return key.toString();}catch(Exception e){return "unavailable";}}
    static Snapshot peek(String scope){Snapshot found=memory;return found!=null&&found.scope.equals(scope)&&System.currentTimeMillis()-found.created<24L*60*60*1000?found:null;}
    static void remember(Snapshot snapshot){memory=snapshot;}
    private static File file(Context c){return new File(c.getCacheDir(),"online-home-v1.json");}
    static Snapshot read(Context c,String scope){Snapshot found=peek(scope);if(found!=null)return found;try{File file=file(c);if(!file.isFile()||file.length()>3*1024*1024)return null;JSONObject json=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8));long date=json.getLong("created");if(!scope.equals(json.optString("scope"))||System.currentTimeMillis()-date>24L*60*60*1000)return null;
        List<YouTubeMusicClient.PlaylistItem> playlists=playlists(json.getJSONArray("playlists"));List<YouTubeMusicClient.HomeSection> sections=new ArrayList<>();JSONArray values=json.getJSONArray("sections");for(int i=0;i<Math.min(40,values.length());i++){JSONObject value=values.getJSONObject(i);YouTubeMusicClient.HomeSection section=new YouTubeMusicClient.HomeSection(value.getString("title"));section.playlists.addAll(playlists(value.getJSONArray("playlists")));JSONArray songs=value.getJSONArray("songs");for(int j=0;j<Math.min(100,songs.length());j++){JSONObject row=songs.getJSONObject(j);Uri uri=Uri.parse(row.getString("uri"));if(!OnlineAudio.isOnline(uri))continue;section.songs.add(new OnlineTrack(uri,row.getString("title"),row.getString("artist"),row.getString("album"),row.getString("name"),row.getLong("duration")));OnlineAudio.rememberImage(uri.getLastPathSegment(),row.optString("image"));}sections.add(section);}found=new Snapshot(scope,date,playlists,sections);if(scope.equals(scope(c)))memory=found;return found;
    }catch(Exception ignored){return null;}}
    static void write(Context c,Snapshot snapshot){try{if(!snapshot.scope.equals(scope(c)))return;JSONArray sections=new JSONArray();for(YouTubeMusicClient.HomeSection section:snapshot.sections){JSONArray songs=new JSONArray();for(OnlineTrack song:section.songs)songs.put(new JSONObject().put("uri",song.uri.toString()).put("title",song.title).put("artist",song.artist).put("album",song.album).put("name",song.name).put("duration",song.duration).put("image",OnlineAudio.imageUrl(song.uri)));sections.put(new JSONObject().put("title",section.title).put("playlists",json(section.playlists)).put("songs",songs));}String json=new JSONObject().put("scope",snapshot.scope).put("created",snapshot.created).put("playlists",json(snapshot.playlists)).put("sections",sections).toString();if(json.length()<1500000)java.nio.file.Files.write(file(c).toPath(),json.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception ignored){}}
    private static JSONArray json(List<YouTubeMusicClient.PlaylistItem> values)throws Exception{JSONArray array=new JSONArray();for(YouTubeMusicClient.PlaylistItem item:values)array.put(new JSONObject().put("id",item.id).put("title",item.title).put("subtitle",item.subtitle).put("image",item.thumbnailUrl));return array;}
    private static List<YouTubeMusicClient.PlaylistItem> playlists(JSONArray values)throws Exception{List<YouTubeMusicClient.PlaylistItem> result=new ArrayList<>();for(int i=0;i<Math.min(200,values.length());i++){JSONObject row=values.getJSONObject(i);result.add(new YouTubeMusicClient.PlaylistItem(row.getString("id"),row.getString("title"),row.getString("subtitle"),row.optString("image")));}return result;}
}

