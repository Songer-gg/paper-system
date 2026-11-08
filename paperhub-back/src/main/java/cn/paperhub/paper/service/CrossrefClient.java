package cn.paperhub.paper.service;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class CrossrefClient {
    static final int MAX_BYTES=2*1024*1024;
    private final ObjectMapper json;
    public CrossrefClient(ObjectMapper json) { this.json=json; }

    /** null means an exact DOI record does not exist; other upstream failures are never logged with request data. */
    public JsonNode lookup(String doi) throws IOException {
        URL address=new URL("https://api.crossref.org/works/"+URLEncoder.encode(doi,"UTF-8"));
        HttpURLConnection connection=open(address);
        try {
            connection.setConnectTimeout(3000);connection.setReadTimeout(4000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("User-Agent","PaperHub/0.1 (DOI metadata lookup)");
            int status=connection.getResponseCode();
            if(status==404) return null;
            if(status!=200 || connection.getContentLengthLong()>MAX_BYTES) throw new IOException("Metadata provider unavailable");
            String contentType=connection.getContentType();
            if(contentType==null || !contentType.toLowerCase(java.util.Locale.ROOT).contains("json")) throw new IOException("Invalid provider format");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            byte[] bytes;
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1) {
                    if(output.size()+count>MAX_BYTES || System.nanoTime()>deadline) throw new IOException("Provider response limit");
                    output.write(buffer,0,count);
                }
                bytes=output.toByteArray();
            }
            int depth=0,tokens=0;
            try(JsonParser parser=json.getFactory().createParser(bytes)) {
                JsonToken token;
                while((token=parser.nextToken())!=null) {
                    if(++tokens>100000) throw new IOException("Provider response limit");
                    if(token==JsonToken.START_OBJECT || token==JsonToken.START_ARRAY) { if(++depth>64) throw new IOException("Provider nesting limit"); }
                    else if(token==JsonToken.END_OBJECT || token==JsonToken.END_ARRAY) depth--;
                }
            }
            JsonNode root=json.readTree(bytes);
            if(root==null || !"ok".equals(root.path("status").asText()) || !root.path("message").isObject()) throw new IOException("Invalid provider response");
            return root.path("message");
        } finally { connection.disconnect(); }
    }
    protected HttpURLConnection open(URL address) throws IOException { return (HttpURLConnection)address.openConnection(); }
}
