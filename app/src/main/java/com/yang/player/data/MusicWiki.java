package com.yang.player.data;

import android.content.Context;
import android.os.*;
import java.io.*;
import java.net.*;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.json.*;

/** Optional encyclopedic excerpts. Never gates the local library or playback. */
public final class MusicWiki {
    public static final class Entry {
        public final String text,full,url,language;
        Entry(JSONObject json){full=excerpt(json.optString("full"),3,650);text=brief(full.isEmpty()?json.optString("text"):full);url=json.optString("url");language=json.optString("language");}
    }
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor worker=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(8),r->new Thread(r,"MusicWiki"));
    private static final android.util.LruCache<String,JSONObject> memory=new android.util.LruCache<>(80);
    private static final Object diskLock=new Object();
    private static long nextRequest;
    public static Future<?> load(Context context,String album,String artist,Consumer<Entry> done){
        final Context app=context.getApplicationContext();final boolean artistPage=album==null;
        final String name=artistPage?artist:release(album),credit=primary(artist);
        if(name==null||name.trim().isEmpty()||name.equals("Sin álbum")||credit.equals("Artista desconocido"))return null;
        final String key=(artistPage?"artist|":"album|")+normalize(name)+"|"+normalize(credit);
        JSONObject hot=memory.get(key);if(fresh(hot)){deliver(done,hot.optJSONObject("entry"));return null;}
        worker.purge();
        try{return worker.submit(()->{
            JSONObject stale=null;boolean delivered=false;
            try{
                File file=cache(app,key);
                synchronized(diskLock){if(file.isFile()&&file.length()<250000){try{
                    JSONObject saved=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8));stale=saved.optJSONObject("entry");
                    memory.put(key,saved);
                    if(fresh(saved)){deliver(done,stale);return;}
                }catch(JSONException invalid){stale=null;}}}
                if(stale!=null){deliver(done,stale);delivered=true;}
                JSONObject found=null;
                for(String language:new String[]{"es","en"}){check();found=find(language,name,credit,artistPage);if(found!=null)break;}
                check();JSONObject saved=new JSONObject().put("schema",2).put("created",System.currentTimeMillis()).put("entry",found==null?JSONObject.NULL:found);memory.put(key,saved);deliver(done,found);delivered=true;
                synchronized(diskLock){check();writeCache(file,saved.toString());trim(file.getParentFile());}
            }catch(Exception unavailable){if(!delivered&&!Thread.currentThread().isInterrupted())deliver(done,stale);}
        });}catch(RejectedExecutionException busy){return null;}
    }
    private static boolean fresh(JSONObject saved){if(saved==null||saved.optInt("schema")!=2)return false;long age=System.currentTimeMillis()-saved.optLong("created");return age>=0&&age<(saved.optJSONObject("entry")==null?86400000L:30L*86400000L);}
    private static void deliver(Consumer<Entry> done,JSONObject json){if(!Thread.currentThread().isInterrupted())main.post(()->done.accept(json==null?null:new Entry(json)));}
    private static JSONObject find(String language,String name,String artist,boolean artistPage)throws Exception{
        String query="\""+name.replace('"',' ')+"\""+(artistPage?"":" \""+artist.replace('"',' ')+"\"");
        String base="https://"+language+".wikipedia.org/w/api.php?action=query&format=json&formatversion=2&maxlag=5";
        // Search and extracts travel together, avoiding a second round trip per language.
        JSONObject response=get(base+"&generator=search&gsrnamespace=0&gsrlimit=3&gsrsearch="+URLEncoder.encode(query,"UTF-8")+"&prop=extracts|pageprops&exintro=1&explaintext=1&exlimit=3&ppprop=disambiguation");
        JSONObject result=response.optJSONObject("query");if(result==null){if(response.has("error"))throw new IOException("Wikipedia no disponible");return null;}
        JSONArray pages=result.optJSONArray("pages");if(pages==null)return null;JSONObject match=null;
        for(int i=0;i<pages.length();i++){
            JSONObject page=pages.getJSONObject(i);JSONObject props=page.optJSONObject("pageprops");
            String title=page.optString("title").replaceAll("\\s*\\([^)]*\\)\\s*$","");if(!normalize(title).equals(normalize(name)))continue;
            if(page.has("missing")||props!=null&&props.has("disambiguation"))continue;
            String raw=page.optString("extract").trim();String text=raw.replaceAll("\\s+"," ").trim();String normalized=normalize(text);
            if(text.length()<35)continue;
            // Require musical context; albums must also name their credited artist.
            String intro=normalized.substring(0,Math.min(normalized.length(),600));
            String terms=artistPage?"singer|songwriter|musician|rapper|band|duo|dj|producer|cantante|compositor|musico|rapero|banda|grupo|productor":"album|ep|extended play|disco|mixtape";
            if(!java.util.regex.Pattern.compile("\\b(?:"+terms+")\\b").matcher(intro).find())continue;
            if(!artistPage&&!normalize(artist).isEmpty()&&!(" "+normalized+" ").contains(" "+normalize(artist)+" "))continue;
            // Ambiguous artist names or album editions are safer left without a description.
            if(match!=null)return null;
            String paragraph=excerpt(raw.split("\\r?\\n\\s*\\r?\\n",2)[0],3,650);
            match=new JSONObject().put("text",brief(paragraph)).put("full",paragraph).put("language",language).put("url","https://"+language+".wikipedia.org/?curid="+page.getLong("pageid"));
        }
        return match;
    }
    private static String brief(String text){return excerpt(text,2,440);}
    private static String excerpt(String value,int maximumSentences,int targetCharacters){
        String text=value.replaceAll("\\s+"," ").trim();if(text.isEmpty())return text;
        java.text.BreakIterator sentences=java.text.BreakIterator.getSentenceInstance(new Locale("es"));sentences.setText(text);int end=0;
        // Character budget is soft: a long first sentence stays whole rather than ending mid-thought.
        for(int i=0;i<maximumSentences;i++){int next=sentences.next();if(next==java.text.BreakIterator.DONE)break;if(next>targetCharacters&&end>0)break;end=next;if(end>=targetCharacters)break;}
        return end>0?text.substring(0,end).trim():text;
    }
    private static String release(String value){return value==null?"":value.replaceAll("(?i)\\s*(?:\\(|\\[)[^)\\]]*(?:deluxe|expanded|remaster|anniversary|special edition|bonus|edicion)[^)\\]]*(?:\\)|\\])","").replaceAll("(?i)\\s*[-–—]\\s*(?:ep|single|deluxe(?: edition)?)\\s*$","").trim();}
    private static String primary(String artist){return artist==null?"":artist.split("(?i)\\s+(?:feat\\.?|ft\\.?|featuring|with)\\s+|\\s*&\\s*|;",2)[0].trim();}
    private static String normalize(String text){return Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
    private static void check()throws InterruptedException{if(Thread.currentThread().isInterrupted())throw new InterruptedException();}
    private static JSONObject get(String url)throws Exception{
        check();while(true){long delay;synchronized(MusicWiki.class){delay=nextRequest-SystemClock.elapsedRealtime();if(delay<=0){nextRequest=SystemClock.elapsedRealtime()+500;break;}}Thread.sleep(Math.min(delay,150));check();}
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent","YangPlayer/1.0 (Android music player; com.yang.player)");connection.setRequestProperty("Accept","application/json");
        try{
            if(connection.getResponseCode()!=200)throw new IOException("Wikipedia no disponible");
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1){check();if(output.size()+n>200000)throw new IOException("Respuesta demasiado grande");output.write(buffer,0,n);}return new JSONObject(output.toString("UTF-8"));
            }
        }finally{connection.disconnect();}
    }
    private static File cache(Context context,String key)throws Exception{
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes("UTF-8"));StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(Locale.ROOT,"%02x",b&255));
        File dir=new File(context.getCacheDir(),"music-wiki-v1");if(!dir.isDirectory())dir.mkdirs();return new File(dir,hex+".json");
    }
    private static void writeCache(File file,String json)throws IOException{
        android.util.AtomicFile atomic=new android.util.AtomicFile(file);FileOutputStream out=atomic.startWrite();
        try{out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));atomic.finishWrite(out);}
        catch(IOException error){atomic.failWrite(out);throw error;}
    }
    private static void trim(File dir){File[] files=dir.listFiles((folder,name)->name.endsWith(".json"));if(files==null||files.length<=150)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));for(int i=0;i<files.length-150;i++)files[i].delete();}
}
