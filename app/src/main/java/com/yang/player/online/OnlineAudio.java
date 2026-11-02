package com.yang.player.online;

import android.net.Uri;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.downloader.*;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.stream.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

/** YouTube identities stay stable; short-lived media URLs are resolved on demand. */
final class OnlineAudio {
    static final String UA="Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36";
    private static boolean initialized;
    private static final Map<String,Resolved> streams=new ConcurrentHashMap<>();
    private static final Map<String,InfoEntry> infoCache=new ConcurrentHashMap<>();
    private static final ExecutorService preparation=new ThreadPoolExecutor(1,1,15,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(1),new ThreadPoolExecutor.DiscardOldestPolicy());
    private static final class InfoEntry {final long created=android.os.SystemClock.elapsedRealtime();final FutureTask<StreamInfo> task;InfoEntry(Uri uri){task=new FutureTask<>(()->StreamInfo.getInfo(NewPipe.getService(0),watch(uri)));}}
    private static StreamInfo info(Uri uri)throws Exception{
        init();String key=id(uri);InfoEntry entry=infoCache.get(key);if(entry!=null&&android.os.SystemClock.elapsedRealtime()-entry.created>5*60*1000){infoCache.remove(key,entry);entry=null;}
        if(entry==null){InfoEntry candidate=new InfoEntry(uri);InfoEntry old=infoCache.putIfAbsent(key,candidate);entry=old==null?candidate:old;if(infoCache.size()>40)for(String other:infoCache.keySet())if(!other.equals(key)){infoCache.remove(other);break;}}
        entry.task.run();try{return entry.task.get();}catch(Exception error){infoCache.remove(key,entry);throw error;}
    }
    static void warm(OnlineTrack song){if(song==null||!isOnline(song.uri))return;preparation.execute(()->{try{Resolved stream=resolve(song.uri);stream.buffer.warm(stream.url);}catch(Exception ignored){}});}
    private static final Map<String,String> images=new ConcurrentHashMap<>();
    static void rememberImage(String key,String url){if(key!=null&&url!=null)images.put(key,url);}
    static String imageUrl(Uri uri){return uri==null?"":images.getOrDefault(uri.getLastPathSegment(),"");}
    static boolean isOnline(Uri uri){return uri!=null&&"yangyoutube".equals(uri.getScheme());}
    static synchronized void init()throws IOException{if(!initialized){NewPipe.init(new HttpDownloader());initialized=true;}}
    static String id(Uri uri)throws IOException{String id=uri.getLastPathSegment();if(!isOnline(uri)||id==null||!id.matches("[A-Za-z0-9_-]{11}"))throw new IOException("Enlace de YouTube no válido.");return id;}
    static void invalidate(Uri uri)throws IOException{String key=id(uri);streams.remove(key);infoCache.remove(key);}
    static String watch(Uri uri)throws IOException{return "https://www.youtube.com/watch?v="+id(uri);}
    static final class Resolved {final OnlineBuffer buffer=new OnlineBuffer();final String url;final long duration,created=android.os.SystemClock.elapsedRealtime();Resolved(String url,long duration){this.url=url;this.duration=duration;}}
    static Resolved resolve(Uri uri)throws IOException{
        init();String key=id(uri);Resolved cached=streams.get(key);if(cached!=null&&android.os.SystemClock.elapsedRealtime()-cached.created<5*60*1000)return cached;
        try{
            StreamInfo info=info(uri);if(info.getStreamType()!=StreamType.VIDEO_STREAM)throw new IOException("Por ahora el modo online admite vídeos publicados, no directos.");
            AudioStream best=null;int score=-1;for(AudioStream audio:info.getAudioStreams()){
                if(!audio.isUrl()||audio.getDeliveryMethod()!=DeliveryMethod.PROGRESSIVE_HTTP)continue;String url=audio.getContent();Uri parsed=Uri.parse(url);String host=parsed.getHost();if(!"https".equals(parsed.getScheme())||host==null||!host.endsWith(".googlevideo.com"))continue;
                int quality=Math.max(audio.getAverageBitrate(),audio.getBitrate()/1000);if(String.valueOf(audio.getAudioTrackType()).contains("ORIGINAL"))quality+=10000;if(quality>score){best=audio;score=quality;}
            }
            if(best==null)throw new IOException("YouTube no entregó un flujo de audio compatible. Puede requerir actualizar NewPipeExtractor o resolver una restricción del servicio.");
            Resolved result=rememberStream(key,new Resolved(best.getContent(),info.getDuration()*1000L));if(!info.getThumbnails().isEmpty())images.put(key,info.getThumbnails().get(0).getUrl());return result;
        }catch(IOException e){throw e;}catch(Exception|LinkageError e){throw new IOException("No se pudo obtener el audio de YouTube. Revisa la conexión; el servicio puede limitar la extracción o requerir una actualización. "+e.getClass().getSimpleName(),e);}
    }
    private static synchronized Resolved rememberStream(String key,Resolved candidate){
        Resolved existing=streams.get(key);if(existing!=null&&android.os.SystemClock.elapsedRealtime()-existing.created<300000)return existing;
        streams.remove(key);while(streams.size()>=40){String oldest=null;long created=Long.MAX_VALUE;for(Map.Entry<String,Resolved> entry:streams.entrySet())if(entry.getValue().created<created){created=entry.getValue().created;oldest=entry.getKey();}if(oldest==null)break;streams.remove(oldest);}
        streams.put(key,candidate);return candidate;
    }
    static final class SearchPage {
        final List<OnlineTrack> songs;final String correctedQuery;
        SearchPage(List<OnlineTrack> songs,String correctedQuery){this.songs=songs;this.correctedQuery=correctedQuery==null?"":correctedQuery;}
    }
    static List<String> suggestions(String query)throws Exception{
        init();String text=query.trim();if(text.length()<2||text.length()>200||text.contains("://"))return Collections.emptyList();
        List<String> result=new ArrayList<>();for(String value:NewPipe.getService(0).getSuggestionExtractor().suggestionList(text)){
            if(value!=null&&!value.trim().isEmpty()&&!result.contains(value))result.add(value);if(result.size()==6)break;
        }return result;
    }
    static List<OnlineTrack> search(String query)throws Exception{return searchPage(query).songs;}
    static List<OnlineTrack> related(OnlineTrack seed)throws Exception{
        init();StreamInfo info=info(seed.uri);List<OnlineTrack> result=new ArrayList<>();
        for(InfoItem item:info.getRelatedItems())if(item instanceof StreamInfoItem){
            StreamInfoItem stream=(StreamInfoItem)item;if(stream.getStreamType()!=StreamType.VIDEO_STREAM||stream.getDuration()<30)continue;
            String key=Uri.parse(stream.getUrl()).getQueryParameter("v");if(key==null||!key.matches("[A-Za-z0-9_-]{11}"))continue;
            result.add(new OnlineTrack(Uri.parse("yangyoutube://watch/"+key),stream.getName(),stream.getUploaderName(),"YouTube",key,stream.getDuration()*1000L));if(!stream.getThumbnails().isEmpty())images.put(key,stream.getThumbnails().get(0).getUrl());if(result.size()==20)break;
        }
        if(result.isEmpty()&&seed.artist!=null&&!seed.artist.isEmpty())result.addAll(search(seed.artist+" music"));return result;
    }
    static SearchPage searchPage(String query)throws Exception{
        init();List<OnlineTrack> songs=new ArrayList<>();StreamingService service=NewPipe.getService(0);
        String video=null;
        if(query.startsWith("https://")||query.startsWith("http://")){
            Uri link=Uri.parse(query);String host=link.getHost();if(host==null||!(host.equals("youtu.be")||host.equals("youtube.com")||host.endsWith(".youtube.com")))throw new IOException("Pega un enlace de YouTube o escribe una búsqueda.");
            if(host.equals("youtu.be"))video=link.getLastPathSegment();else if(link.getPath()!=null&&(link.getPath().startsWith("/shorts/")||link.getPath().startsWith("/live/")))video=link.getLastPathSegment();else video=link.getQueryParameter("v");
            if(video==null||!video.matches("[A-Za-z0-9_-]{11}"))throw new IOException("Usa un enlace a un vídeo individual de YouTube.");
            Uri uri=Uri.parse("yangyoutube://watch/"+video);StreamInfo info=StreamInfo.getInfo(service,watch(uri));if(!info.getThumbnails().isEmpty())images.put(video,info.getThumbnails().get(0).getUrl());songs.add(new OnlineTrack(uri,info.getName(),info.getUploaderName(),"YouTube",video,info.getDuration()*1000L));return new SearchPage(songs,"");
        }
        SearchExtractor extractor=service.getSearchExtractor(query,Collections.singletonList("videos"),"");
        // This overload of SearchInfo.getInfo reads an already fetched extractor.
        // Without fetchPage, NewPipe collects the uninitialized-page exceptions
        // internally and returns an empty list instead of throwing them.
        extractor.fetchPage();
        SearchInfo found=SearchInfo.getInfo(extractor);
        if(found.getRelatedItems().isEmpty()&&!found.getErrors().isEmpty()){
            Throwable cause=found.getErrors().get(0);
            android.util.Log.w("V60Online","No search results could be extracted",cause);
            throw new IOException("No se pudieron leer los resultados de YouTube. Revisa la conexión; el servicio puede requerir actualizar NewPipeExtractor. ("+cause.getClass().getSimpleName()+")",cause);
        }
        for(InfoItem item:found.getRelatedItems())if(item instanceof StreamInfoItem){StreamInfoItem stream=(StreamInfoItem)item;if(stream.getStreamType()!=StreamType.VIDEO_STREAM)continue;Uri link=Uri.parse(stream.getUrl());String key=link.getQueryParameter("v");if(key==null||!key.matches("[A-Za-z0-9_-]{11}"))continue;Uri uri=Uri.parse("yangyoutube://watch/"+key);songs.add(new OnlineTrack(uri,stream.getName(),stream.getUploaderName(),"YouTube",key,Math.max(0,stream.getDuration())*1000L));if(!stream.getThumbnails().isEmpty())images.put(key,stream.getThumbnails().get(0).getUrl());if(songs.size()>=30)break;}
        return new SearchPage(songs,found.isCorrectedSearch()?found.getSearchSuggestion():"");
    }
    static byte[] artwork(Uri uri)throws Exception{String image=images.get(id(uri));if(image==null)return null;HttpURLConnection c=(HttpURLConnection)new URL(image).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(12000);try(InputStream in=c.getInputStream()){return read(in,4*1024*1024);}finally{c.disconnect();}}
    private static byte[] read(InputStream in,int limit)throws IOException{if(in==null)return new byte[0];ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] data=new byte[8192];int n;while((n=in.read(data))!=-1){if(out.size()+n>limit)throw new IOException("La respuesta online es demasiado grande.");out.write(data,0,n);}return out.toByteArray();}
    private static final class HttpDownloader extends Downloader {
        public Response execute(Request request)throws IOException{
            URL url=new URL(request.url());for(int redirects=0;redirects<6;redirects++){
                if(!"https".equals(url.getProtocol()))throw new IOException("El servicio devolvió una conexión no segura.");HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestMethod(request.httpMethod());c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Accept-Encoding","identity");
                for(Map.Entry<String,List<String>> header:request.headers().entrySet())for(String value:header.getValue())c.addRequestProperty(header.getKey(),value);
                try{byte[] body=request.dataToSend();if(body!=null&&body.length>0){c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(body);}}int code=c.getResponseCode();if(code>=300&&code<400&&c.getHeaderField("Location")!=null){url=new URL(url,c.getHeaderField("Location"));continue;}if(code==429)throw new IOException("YouTube limitó las peticiones. Espera antes de reintentar.");InputStream raw=code>=400?c.getErrorStream():c.getInputStream();String text="";if(raw!=null)try(InputStream in="gzip".equalsIgnoreCase(c.getContentEncoding())?new GZIPInputStream(raw):raw){text=new String(read(in,12*1024*1024),java.nio.charset.StandardCharsets.UTF_8);}return new Response(code,c.getResponseMessage(),c.getHeaderFields(),text,url.toString());}finally{c.disconnect();}
            }throw new IOException("Demasiadas redirecciones del servicio.");
        }
    }
}

