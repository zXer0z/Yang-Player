package com.yang.player.online;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Client for YouTube Music InnerTube API (used for personal playlists & home recommendations). */
final class YouTubeMusicClient {
    private static final String INNERTUBE_URL = "https://music.youtube.com/youtubei/v1/";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    static final class PlaylistItem {
        final String id;
        final String title;
        final String subtitle;
        final String thumbnailUrl;
        PlaylistItem(String id, String title, String subtitle, String thumbnailUrl) {
            this.id = id;
            this.title = title != null ? title : "Playlist";
            this.subtitle = subtitle != null ? subtitle : "";
            this.thumbnailUrl = thumbnailUrl;
        }
    }

    static final class HomeSection {
        final String title;
        final List<OnlineTrack> songs = new ArrayList<>();
        final List<PlaylistItem> playlists = new ArrayList<>();
        HomeSection(String title) {
            this.title = title;
        }
    }

    private static JSONObject makeContext() throws Exception {
        JSONObject ctx = new JSONObject();
        JSONObject client = new JSONObject();
        client.put("clientName", "WEB_REMIX");
        client.put("clientVersion", "1.20240101.01.00");
        client.put("hl", "es");
        client.put("gl", "ES");
        ctx.put("client", client);
        return ctx;
    }

    private static HttpURLConnection post(Context c, String endpoint, JSONObject body) throws Exception {
        return post(c,endpoint,body,YouTubeAuth.isLoggedIn(c)?YouTubeAuth.getCookie(c):null);
    }
    private static HttpURLConnection post(Context c,String endpoint,JSONObject body,String cookie)throws Exception{
        URL url = new URL(INNERTUBE_URL + endpoint + "?prettyPrint=false");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", UA);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Origin", "https://music.youtube.com");
        conn.setRequestProperty("Origin", "https://music.youtube.com");
        conn.setRequestProperty("Referer", "https://music.youtube.com/");
        conn.setRequestProperty("Accept-Encoding", "identity");

        boolean auth = cookie!=null&&!cookie.isEmpty();
        if (auth) {
            String sapisid = YouTubeAuth.sapisid(cookie);
            String hash = YouTubeAuth.sapisidHash(sapisid);
            if (cookie != null) conn.setRequestProperty("Cookie", cookie);
            if (hash != null) conn.setRequestProperty("Authorization", "SAPISIDHASH " + hash);
            conn.setRequestProperty("X-Goog-AuthUser", "0");
        }
        android.util.Log.i("V60YouTube", "InnerTube POST " + endpoint + " (auth=" + auth + ")");

        conn.setDoOutput(true);
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(payload);
        }
        return conn;
    }

    private static JSONObject fetchJson(Context c,String endpoint,JSONObject body)throws Exception{
        JSONObject response=readResponse(post(c,endpoint,body));
        if(YouTubeAuth.isLoggedIn(c)&&Boolean.FALSE.equals(sessionState(response))){
            c.getSharedPreferences("youtube_auth",0).edit().putBoolean("server_verified",false).apply();
            throw new IOException("YouTube rechazó la sesión guardada. Vuelve a conectar tu cuenta.");
        }return response;
    }
    private static JSONObject readResponse(HttpURLConnection connection)throws Exception{
        try{int code=connection.getResponseCode();
            if(code>=400)throw new IOException("YouTube respondió HTTP "+code+". No se pudo cargar la cuenta.");
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();try(InputStream input=connection.getInputStream()){byte[] bytes=new byte[8192];int n;while((n=input.read(bytes))!=-1){if(buffer.size()+n>8*1024*1024)throw new IOException("La respuesta de YouTube es demasiado grande.");buffer.write(bytes,0,n);}}
            JSONObject json=new JSONObject(buffer.toString(StandardCharsets.UTF_8.name()));
            if(json.has("error"))throw new IOException("YouTube no pudo completar la consulta ("+json.getJSONObject("error").optString("status","error")+").");
            return json;
        }finally{connection.disconnect();}
    }
    static boolean sessionValid(Context c,String cookie)throws Exception{
        if(YouTubeAuth.sapisid(cookie)==null)return false;
        JSONObject request=new JSONObject().put("context",makeContext()).put("browseId","FEmusic_home");
        Boolean state=sessionState(readResponse(post(c,"browse",request,cookie)));
        if(state==null)throw new IOException("YouTube no confirmó el estado de la sesión. Inténtalo de nuevo.");return state;
    }
    static Boolean sessionState(JSONObject response){return loggedFlag(response.optJSONObject("responseContext"));}
    static JSONObject accountProfile(Context c)throws Exception{
        if(!YouTubeAuth.isLoggedIn(c))throw new IOException("Conecta tu cuenta de YouTube.");
        JSONObject response=fetchJson(c,"account/account_menu",new JSONObject().put("context",makeContext()));
        JSONObject header=findAccountHeader(response);if(header==null)throw new IOException("YouTube no devolvió los datos de tu perfil.");
        String name=runsText(header.optJSONObject("accountName")),handle=runsText(header.optJSONObject("channelHandle"));String photo="";
        JSONObject image=header.optJSONObject("accountPhoto");JSONArray thumbnails=image==null?null:image.optJSONArray("thumbnails");if(thumbnails!=null&&thumbnails.length()>0)photo=thumbnails.getJSONObject(thumbnails.length()-1).optString("url");
        if(name.trim().isEmpty())throw new IOException("El perfil no tiene un nombre disponible.");
        return new JSONObject().put("name",name).put("handle",handle).put("photo",photo);
    }
    private static JSONObject findAccountHeader(Object node){
        if(node instanceof JSONObject){JSONObject object=(JSONObject)node;JSONObject header=object.optJSONObject("activeAccountHeaderRenderer");if(header!=null)return header;for(Iterator<String> keys=object.keys();keys.hasNext();){JSONObject found=findAccountHeader(object.opt(keys.next()));if(found!=null)return found;}}
        else if(node instanceof JSONArray){JSONArray array=(JSONArray)node;for(int i=0;i<array.length();i++){JSONObject found=findAccountHeader(array.opt(i));if(found!=null)return found;}}return null;
    }
    private static Boolean loggedFlag(Object object){
        if(object instanceof JSONObject){JSONObject json=(JSONObject)object;
            if("logged_in".equals(json.optString("key"))){String value=json.optString("value");if("1".equals(value))return true;if("0".equals(value))return false;}
            if(json.has("loggedOut"))return !json.optBoolean("loggedOut",true);
            for(Iterator<String> keys=json.keys();keys.hasNext();){Boolean flag=loggedFlag(json.opt(keys.next()));if(flag!=null)return flag;}
        }else if(object instanceof JSONArray){JSONArray array=(JSONArray)object;for(int i=0;i<array.length();i++){Boolean flag=loggedFlag(array.opt(i));if(flag!=null)return flag;}}return null;
    }

    static List<PlaylistItem> fetchUserPlaylists(Context c) throws Exception {
        if(!YouTubeAuth.isLoggedIn(c))throw new IOException("Conecta y verifica tu cuenta de YouTube antes de cargar sus playlists.");
        List<PlaylistItem> playlists = new ArrayList<>();

        // FEmusic_library_landing is the stable endpoint that returns 200
        JSONObject req2 = new JSONObject();
        req2.put("context", makeContext());
        req2.put("browseId", "FEmusic_library_landing");
        JSONObject res2 = fetchJson(c, "browse", req2);
        findPlaylistsRecursive(res2, playlists);

        // Fallback: try FEmusic_library_playlists with params
        if (playlists.isEmpty()) {
            android.util.Log.i("V60YouTube", "Trying FEmusic_library_playlists for playlists...");
            JSONObject req = new JSONObject();
            req.put("context", makeContext());
            req.put("browseId", "FEmusic_library_playlists");
            req.put("params", "EgWmAQIYASAA");
            JSONObject res = fetchJson(c, "browse", req);
            findPlaylistsRecursive(res, playlists);
        }
        android.util.Log.i("V60YouTube", "Found playlists: " + playlists.size());
        return playlists;
    }

    private static void findPlaylistsRecursive(Object current, List<PlaylistItem> result) {
        if (current instanceof JSONObject) {
            JSONObject obj = (JSONObject) current;
            if (obj.has("musicTwoRowItemRenderer")) {
                PlaylistItem item = parsePlaylistTwoRow(obj.optJSONObject("musicTwoRowItemRenderer"));
                if (item != null && !containsPlaylist(result, item.id)) result.add(item);
                return;
            }
            if (obj.has("musicResponsiveListItemRenderer")) {
                PlaylistItem item = parsePlaylistResponsive(obj.optJSONObject("musicResponsiveListItemRenderer"));
                if (item != null && !containsPlaylist(result, item.id)) result.add(item);
                return;
            }
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                findPlaylistsRecursive(obj.opt(keys.next()), result);
            }
        } else if (current instanceof JSONArray) {
            JSONArray arr = (JSONArray) current;
            for (int i = 0; i < arr.length(); i++) {
                findPlaylistsRecursive(arr.opt(i), result);
            }
        }
    }

    private static boolean containsPlaylist(List<PlaylistItem> list, String id) {
        for (PlaylistItem p : list) if (p.id.equals(id)) return true;
        return false;
    }

    private static PlaylistItem parsePlaylistResponsive(JSONObject renderer) {
        if (renderer == null) return null;
        JSONArray flexColumns = renderer.optJSONArray("flexColumns");
        if (flexColumns == null || flexColumns.length() == 0) return null;

        String browseId = "";
        String title = "";
        String subtitle = "";

        JSONObject col0 = flexColumns.optJSONObject(0);
        if (col0 != null) {
            JSONObject r0 = col0.optJSONObject("musicResponsiveListItemFlexColumnRenderer");
            if (r0 != null) {
                JSONObject tObj = r0.optJSONObject("text");
                title = runsText(tObj);
                if (tObj != null) {
                    JSONArray runs = tObj.optJSONArray("runs");
                    if (runs != null && runs.length() > 0) {
                        JSONObject nav = runs.optJSONObject(0).optJSONObject("navigationEndpoint");
                        if (nav != null && nav.has("browseEndpoint")) {
                            browseId = nav.optJSONObject("browseEndpoint").optString("browseId", "");
                        }
                    }
                }
            }
        }

        if (browseId.isEmpty()) {
            JSONObject nav = renderer.optJSONObject("navigationEndpoint");
            if (nav != null && nav.has("browseEndpoint")) {
                browseId = nav.optJSONObject("browseEndpoint").optString("browseId", "");
            }
        }

        if (browseId.isEmpty() || !(browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.equals("LM") || browseId.equals("VLLM") || browseId.startsWith("RD"))) {
            return null;
        }

        if (flexColumns.length() > 1) {
            JSONObject col1 = flexColumns.optJSONObject(1);
            if (col1 != null) {
                JSONObject r1 = col1.optJSONObject("musicResponsiveListItemFlexColumnRenderer");
                if (r1 != null) {
                    subtitle = runsText(r1.optJSONObject("text"));
                }
            }
        }

        String thumb = bestThumbnail(renderer.optJSONObject("thumbnail"));
        return new PlaylistItem(browseId, title.isEmpty() ? "Playlist" : title, subtitle, thumb);
    }

    private static PlaylistItem parsePlaylistTwoRow(JSONObject renderer) {
        if (renderer == null) return null;
        JSONObject nav = renderer.optJSONObject("navigationEndpoint");
        if (nav == null) return null;
        JSONObject browse = nav.optJSONObject("browseEndpoint");
        if (browse == null) return null;
        String browseId = browse.optString("browseId", "");
        if (!(browseId.startsWith("VL")||browseId.startsWith("PL")||browseId.startsWith("RD")||browseId.equals("LM"))) return null;

        String title = runsText(renderer.optJSONObject("title"));
        String subtitle = runsText(renderer.optJSONObject("subtitle"));
        String thumb = bestThumbnail(renderer.optJSONObject("thumbnailRenderer"));
        return new PlaylistItem(browseId, title, subtitle, thumb);
    }

    static List<HomeSection> fetchHome(Context c) throws Exception {
        JSONObject req = new JSONObject();
        req.put("context", makeContext());
        req.put("browseId", "FEmusic_home");

        JSONObject res = fetchJson(c, "browse", req);
        List<HomeSection> sections = new ArrayList<>();

        JSONObject single = res.optJSONObject("contents");
        if (single == null) return sections;
        JSONObject browseResults = single.optJSONObject("singleColumnBrowseResultsRenderer");
        if (browseResults == null) return sections;
        JSONArray tabs = browseResults.optJSONArray("tabs");
        if (tabs == null || tabs.length() == 0) return sections;
        JSONObject tab = tabs.optJSONObject(0).optJSONObject("tabRenderer");
        if (tab == null) return sections;
        JSONObject sectionList = tab.optJSONObject("content").optJSONObject("sectionListRenderer");
        if (sectionList == null) return sections;
        JSONArray contents = sectionList.optJSONArray("contents");
        if (contents == null) return sections;

        for (int i = 0; i < contents.length(); i++) {
            JSONObject sectionObj = contents.optJSONObject(i);
            if (sectionObj == null) continue;
            JSONObject carousel = sectionObj.optJSONObject("musicCarouselShelfRenderer");
            if (carousel == null) continue;

            String headerTitle = "Recomendados";
            JSONObject header = carousel.optJSONObject("header");
            if (header != null) {
                JSONObject basicHeader = header.optJSONObject("musicCarouselShelfBasicHeaderRenderer");
                if (basicHeader != null) {
                    headerTitle = runsText(basicHeader.optJSONObject("title"));
                }
            }
            if (headerTitle.isEmpty()) headerTitle = "Recomendaciones";

            HomeSection section = new HomeSection(headerTitle);
            JSONArray items = carousel.optJSONArray("contents");
            if (items != null) {
                for (int j = 0; j < items.length(); j++) {
                    JSONObject itemObj = items.optJSONObject(j);
                    if (itemObj == null) continue;

                    JSONObject twoRow = itemObj.optJSONObject("musicTwoRowItemRenderer");
                    if (twoRow != null) {
                        JSONObject nav = twoRow.optJSONObject("navigationEndpoint");
                        if (nav != null && nav.has("watchEndpoint")) {
                            OnlineTrack song = parseWatchEndpoint(twoRow, nav.optJSONObject("watchEndpoint"));
                            if (song != null) section.songs.add(song);
                        } else if (nav != null && nav.has("browseEndpoint")) {
                            PlaylistItem pl = parsePlaylistTwoRow(twoRow);
                            if (pl != null) section.playlists.add(pl);
                        }
                    }

                    JSONObject responsive = itemObj.optJSONObject("musicResponsiveListItemRenderer");
                    if (responsive != null) {
                        OnlineTrack song = parseResponsiveSong(responsive);
                        if (song != null) section.songs.add(song);
                    }
                }
            }

            if (!section.songs.isEmpty() || !section.playlists.isEmpty()) {
                sections.add(section);
            }
        }
        return sections;
    }

    static List<OnlineTrack> fetchPlaylistTracks(Context c, String playlistId) throws Exception {
        JSONObject req = new JSONObject();
        req.put("context", makeContext());
        String browseId = playlistId.startsWith("VL") ? playlistId : "VL" + playlistId;
        req.put("browseId", browseId);

        JSONObject res = fetchJson(c, "browse", req);
        List<OnlineTrack> songs = new ArrayList<>();
        findSongsRecursive(res, songs);
        return songs;
    }

    private static void findSongsRecursive(Object current, List<OnlineTrack> result) {
        if (current instanceof JSONObject) {
            JSONObject obj = (JSONObject) current;
            if (obj.has("musicResponsiveListItemRenderer")) {
                OnlineTrack s = parseResponsiveSong(obj.optJSONObject("musicResponsiveListItemRenderer"));
                if (s != null) result.add(s);
                return;
            }
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                findSongsRecursive(obj.opt(keys.next()), result);
            }
        } else if (current instanceof JSONArray) {
            JSONArray arr = (JSONArray) current;
            for (int i = 0; i < arr.length(); i++) {
                findSongsRecursive(arr.opt(i), result);
            }
        }
    }

    private static OnlineTrack parseResponsiveSong(JSONObject item) {
        if (item == null) return null;
        String videoId = item.optString("playlistItemData", "");
        if (item.has("playlistItemData")) {
            JSONObject data = item.optJSONObject("playlistItemData");
            if (data != null) videoId = data.optString("videoId", "");
        }

        JSONArray flexColumns = item.optJSONArray("flexColumns");
        if (flexColumns == null || flexColumns.length() == 0) return null;

        String title = "";
        String artist = "YouTube";

        JSONObject col0 = flexColumns.optJSONObject(0);
        if (col0 != null) {
            JSONObject r0 = col0.optJSONObject("musicResponsiveListItemFlexColumnRenderer");
            if (r0 != null) {
                JSONObject tObj = r0.optJSONObject("text");
                title = runsText(tObj);
                if (videoId.isEmpty() && tObj != null) {
                    JSONArray runs = tObj.optJSONArray("runs");
                    if (runs != null && runs.length() > 0) {
                        JSONObject nav = runs.optJSONObject(0).optJSONObject("navigationEndpoint");
                        if (nav != null && nav.has("watchEndpoint")) {
                            videoId = nav.optJSONObject("watchEndpoint").optString("videoId", "");
                        }
                    }
                }
            }
        }

        if (flexColumns.length() > 1) {
            JSONObject col1 = flexColumns.optJSONObject(1);
            if (col1 != null) {
                JSONObject r1 = col1.optJSONObject("musicResponsiveListItemFlexColumnRenderer");
                if (r1 != null) {
                    artist = runsText(r1.optJSONObject("text"));
                }
            }
        }

        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) return null;

        long durationMs = 0;
        JSONArray fixedColumns = item.optJSONArray("fixedColumns");
        if (fixedColumns != null && fixedColumns.length() > 0) {
            JSONObject f0 = fixedColumns.optJSONObject(0).optJSONObject("musicResponsiveListItemFixedColumnRenderer");
            if (f0 != null) {
                String durStr = runsText(f0.optJSONObject("text"));
                durationMs = parseDuration(durStr);
            }
        }

        String thumb = bestThumbnail(item.optJSONObject("thumbnail"));
        if (thumb != null && !thumb.isEmpty()) {
            OnlineAudio.rememberImage(videoId, thumb);
        }

        Uri uri = Uri.parse("yangyoutube://watch/" + videoId);
        return new OnlineTrack(uri, title.isEmpty() ? "Canción" : title, artist.isEmpty() ? "YouTube" : artist, "YouTube", videoId, durationMs);
    }

    private static OnlineTrack parseWatchEndpoint(JSONObject twoRow, JSONObject watchEndpoint) {
        String videoId = watchEndpoint.optString("videoId", "");
        if (videoId.isEmpty() || !videoId.matches("[A-Za-z0-9_-]{11}")) return null;
        String title = runsText(twoRow.optJSONObject("title"));
        String artist = runsText(twoRow.optJSONObject("subtitle"));
        String thumb = bestThumbnail(twoRow.optJSONObject("thumbnailRenderer"));
        if (thumb != null && !thumb.isEmpty()) {
            OnlineAudio.rememberImage(videoId, thumb);
        }
        Uri uri = Uri.parse("yangyoutube://watch/" + videoId);
        return new OnlineTrack(uri, title.isEmpty() ? "Vídeo musical" : title, artist.isEmpty() ? "YouTube" : artist, "YouTube", videoId, 0);
    }

    private static String runsText(JSONObject obj) {
        if (obj == null) return "";
        JSONArray runs = obj.optJSONArray("runs");
        if (runs != null && runs.length() > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < runs.length(); i++) {
                JSONObject r = runs.optJSONObject(i);
                if (r != null && r.has("text")) {
                    sb.append(r.optString("text"));
                }
            }
            return sb.toString().trim();
        }
        return obj.optString("simpleText", "").trim();
    }

    private static String bestThumbnail(JSONObject renderer) {return bestThumbnail(renderer,0);}
    private static String bestThumbnail(JSONObject renderer,int depth) {
        if(renderer==null||depth>4)return null;
        JSONArray images=renderer.optJSONArray("thumbnails");
        if(images!=null){String best=null;int distance=Integer.MAX_VALUE;for(int i=0;i<images.length();i++){JSONObject item=images.optJSONObject(i);if(item==null)continue;String url=item.optString("url");if(!url.startsWith("https://"))continue;int next=Math.abs(item.optInt("width",400)-400);if(next<distance){distance=next;best=url;}}if(best!=null)return best;}
        for(String key:new String[]{"musicThumbnailRenderer","thumbnail","thumbnailRenderer","croppedSquareThumbnailRenderer"}){String found=bestThumbnail(renderer.optJSONObject(key),depth+1);if(found!=null)return found;}return null;
    }

    private static long parseDuration(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        String[] parts = text.trim().split(":");
        try {
            if (parts.length == 2) {
                long m = Long.parseLong(parts[0]);
                long s = Long.parseLong(parts[1]);
                return (m * 60 + s) * 1000L;
            } else if (parts.length == 3) {
                long h = Long.parseLong(parts[0]);
                long m = Long.parseLong(parts[1]);
                long s = Long.parseLong(parts[2]);
                return (h * 3600 + m * 60 + s) * 1000L;
            }
        } catch (Exception ignored) {}
        return 0;
    }
}

