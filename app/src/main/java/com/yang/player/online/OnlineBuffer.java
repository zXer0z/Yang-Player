package com.yang.player.online;
import java.io.*;
import java.net.*;

/** Small in-memory intro, shared by the probe, decoder and next-track preparation. */
final class OnlineBuffer {
    static final int LIMIT=96*1024;
    static final class Prefix {final byte[] data;final long total;final String type;Prefix(byte[] data,long total,String type){this.data=data;this.total=total;this.type=type;}}
    volatile Prefix prefix;
    synchronized void remember(byte[] bytes,long total,String type){if(total>0&&bytes.length>0&&(prefix==null||bytes.length>prefix.data.length))prefix=new Prefix(bytes,total,type);}
    void warm(String url){
        if(prefix!=null)return;HttpURLConnection connection=null;
        try{connection=(HttpURLConnection)new URL(url).openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(6000);connection.setReadTimeout(6000);connection.setRequestProperty("User-Agent",OnlineAudio.UA);connection.setRequestProperty("Range","bytes=0-"+(LIMIT-1));connection.setRequestProperty("Accept-Encoding","identity");int status=connection.getResponseCode();if(status!=206&&status!=200)return;
            if(status==206&&(connection.getHeaderField("Content-Range")==null||!connection.getHeaderField("Content-Range").startsWith("bytes 0-")))return;long total=total(connection);ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] data=new byte[16384];int n;while(out.size()<LIMIT&&(n=in.read(data,0,Math.min(data.length,LIMIT-out.size())))>0)out.write(data,0,n);}remember(out.toByteArray(),total,connection.getContentType());
        }catch(IOException ignored){}finally{if(connection!=null)connection.disconnect();}
    }
    static long total(HttpURLConnection connection){String range=connection.getHeaderField("Content-Range");try{if(range!=null)return Long.parseLong(range.substring(range.lastIndexOf('/')+1));return connection.getContentLengthLong();}catch(Exception ignored){return -1;}}
}

