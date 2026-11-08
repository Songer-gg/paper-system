package cn.paperhub.paper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrossrefClientTest {
    HttpURLConnection connection(int status,String body) throws Exception {
        HttpURLConnection value=mock(HttpURLConnection.class);when(value.getResponseCode()).thenReturn(status);
        when(value.getContentType()).thenReturn("application/json; charset=utf-8");when(value.getContentLengthLong()).thenReturn(-1L);
        when(value.getInputStream()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));return value;
    }
    CrossrefClient client(HttpURLConnection value,AtomicReference<URL> address) {
        return new CrossrefClient(new ObjectMapper()) { @Override protected HttpURLConnection open(URL url) { address.set(url);return value; } };
    }
    @Test void onlyFixedHttpsOriginIsUsedAndDoiCannotInjectAQueryRedirectOrCredentials() throws Exception {
        HttpURLConnection connection=connection(200,"{\"status\":\"ok\",\"message\":{\"DOI\":\"10.1234/test\"}}");AtomicReference<URL> address=new AtomicReference<>();
        assertNotNull(client(connection,address).lookup("10.1234/path?target=http://127.0.0.1/#secret"));
        assertEquals("https",address.get().getProtocol());assertEquals("api.crossref.org",address.get().getHost());
        assertNull(address.get().getQuery());assertNull(address.get().getRef());assertTrue(address.get().getPath().startsWith("/works/10.1234%2F"));
        verify(connection).setInstanceFollowRedirects(false);verify(connection).setConnectTimeout(3000);verify(connection).setReadTimeout(4000);
        verify(connection,never()).setRequestProperty(eq("Authorization"),anyString());verify(connection).disconnect();
    }
    @Test void notFoundIsDistinctAndRedirectsErrorsAndRateLimitsAreNotFollowed() throws Exception {
        assertNull(client(connection(404,""),new AtomicReference<>()).lookup("10.1234/test"));
        for(int status:new int[]{301,302,429,500}) {
            HttpURLConnection connection=connection(status,"provider-private-error");
            assertThrows(IOException.class,()->client(connection,new AtomicReference<>()).lookup("10.1234/test"));
            verify(connection,never()).getInputStream();verify(connection).disconnect();
        }
    }
    @Test void oversizedBodiesMalformedJsonAndWrongFormatsAreRejected() throws Exception {
        for(String body:new String[]{"{", "{\"status\":\"ok\"}", "[1,2]", new String(new char[CrossrefClient.MAX_BYTES+1]).replace('\0','a')}) {
            assertThrows(IOException.class,()->client(connection(200,body),new AtomicReference<>()).lookup("10.1234/test"));
        }
        HttpURLConnection oversized=connection(200,"{}");when(oversized.getContentLengthLong()).thenReturn((long)CrossrefClient.MAX_BYTES+1);
        assertThrows(IOException.class,()->client(oversized,new AtomicReference<>()).lookup("10.1234/test"));verify(oversized,never()).getInputStream();
        HttpURLConnection html=connection(200,"<html>error</html>");when(html.getContentType()).thenReturn("text/html");
        assertThrows(IOException.class,()->client(html,new AtomicReference<>()).lookup("10.1234/test"));
    }
    @Test void deepJsonAndReadTimeoutsAreBoundedAndConnectionsAreClosed() throws Exception {
        String deep="";for(int i=0;i<70;i++) deep+="[";deep+="0";for(int i=0;i<70;i++) deep+="]";
        HttpURLConnection nested=connection(200,deep);
        assertThrows(IOException.class,()->client(nested,new AtomicReference<>()).lookup("10.1234/test"));verify(nested).disconnect();
        HttpURLConnection timeout=connection(200,"{}");when(timeout.getResponseCode()).thenThrow(new SocketTimeoutException());
        assertThrows(IOException.class,()->client(timeout,new AtomicReference<>()).lookup("10.1234/test"));verify(timeout).disconnect();
    }
}
