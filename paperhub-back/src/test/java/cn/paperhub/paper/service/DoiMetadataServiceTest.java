package cn.paperhub.paper.service;

import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.paper.vo.PaperMetadataView;
import cn.paperhub.support.MutableClock;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DoiMetadataServiceTest {
    final ObjectMapper json=new ObjectMapper();final MutableClock clock=new MutableClock();
    final CrossrefClient client=mock(CrossrefClient.class);
    DoiMetadataService service(boolean enabled) { return new DoiMetadataService(client,new PaperValidation(clock),clock,enabled); }
    ObjectNode record(String doi) throws Exception {
        ObjectNode value=(ObjectNode)json.readTree("{\"DOI\":\"10.1234/test\",\"title\":[\"Abstract Algebra &amp; Learning\"],\"author\":[{\"given\":\"John\",\"family\":\"Smith\"},{\"literal\":\"Example Team\"}],\"container-title\":[\"Test Science\"],\"published-print\":{\"date-parts\":[[2025,6,3]]},\"subject\":[\"Not author keywords\"],\"abstract\":\"<jats:title>Abstract</jats:title><jats:p>Original <jats:italic>scientific</jats:italic> summary &amp; results.</jats:p><jats:p>Second paragraph.</jats:p>\"}");
        value.put("DOI",doi);return value;
    }
    @Test void exactDoiMapsJatsAbstractAuthorsAndPublicationFieldsWithoutInventingKeywords() throws Exception {
        when(client.lookup("10.1234/test")).thenReturn(record("10.1234/test"));
        PaperMetadataView value=service(true).lookup(" https://doi.org/10.1234/TEST ");
        assertEquals("found",value.lookupStatus);assertEquals("Abstract Algebra & Learning",value.title);
        assertEquals("Original scientific summary & results.\nSecond paragraph.",value.abstractText);
        assertEquals("John Smith",value.authors.get(0));assertEquals("Example Team",value.authors.get(1));
        assertEquals(2025,value.year);assertTrue(value.keywords.isEmpty());assertEquals("crossref",value.sources.get("abstract"));
    }
    @Test void missingAbstractIsExplicitAndNotReplacedByTheTitle() throws Exception {
        ObjectNode record=record("10.1234/test");record.remove("abstract");when(client.lookup(anyString())).thenReturn(record);
        PaperMetadataView value=service(true).lookup("10.1234/test");
        assertEquals("found",value.lookupStatus);assertEquals("",value.abstractText);assertTrue(value.warnings.stream().anyMatch(note->note.contains("未提供可用摘要")));
    }
    @Test void explicitNullAbstractDoesNotBecomeTheLiteralWordNull() throws Exception {
        ObjectNode value=record("10.1234/test");value.putNull("abstract");when(client.lookup(anyString())).thenReturn(value);
        assertEquals("",service(true).lookup("10.1234/test").abstractText);
    }
    @Test void successfulCacheIsBoundedExpiresAndDoesNotExposeMutableCachedObjects() throws Exception {
        when(client.lookup(anyString())).thenAnswer(call->record(call.getArgument(0)));
        DoiMetadataService service=service(true);PaperMetadataView first=service.lookup("10.1234/test");first.title="changed";first.authors.clear();
        assertFalse(service.lookup("10.1234/test").authors.isEmpty());assertNotEquals("changed",service.lookup("10.1234/test").title);
        verify(client,times(1)).lookup("10.1234/test");
        clock.advance(Duration.ofMinutes(31));service.lookup("10.1234/test");verify(client,times(2)).lookup("10.1234/test");
        for(int i=0;i<129;i++) service.lookup("10.1234/entry"+i);
        service.lookup("10.1234/test");verify(client,times(3)).lookup("10.1234/test");
    }
    @Test void notFoundCacheExpiresSoonerAndTransientFailuresAreNotCached() throws Exception {
        when(client.lookup(anyString())).thenReturn(null).thenThrow(new IOException("private-upstream-detail")).thenReturn(record("10.1234/test"));
        DoiMetadataService service=service(true);assertEquals("not-found",service.lookup("10.1234/test").lookupStatus);
        assertEquals("not-found",service.lookup("10.1234/test").lookupStatus);verify(client,times(1)).lookup(anyString());
        clock.advance(Duration.ofMinutes(6));PaperMetadataView failure=service.lookup("10.1234/test");
        assertEquals("unavailable",failure.lookupStatus);assertFalse(failure.warnings.toString().contains("private-upstream-detail"));
        assertEquals("found",service.lookup("10.1234/test").lookupStatus);
    }
    @Test void disabledAndInvalidRequestsDoNotContactTheProvider() {
        assertEquals("disabled",service(false).lookup("10.1234/test").lookupStatus);
        assertThrows(BusinessException.class,()->service(true).lookup("http://localhost/private"));
        assertThrows(BusinessException.class,()->service(true).lookup(""));verifyNoInteractions(client);
    }
    @Test void mismatchedRecordsAndMalformedFieldsBecomeGracefulUnavailableResults() throws Exception {
        when(client.lookup(anyString())).thenReturn(record("10.1234/wrong"));
        assertEquals("unavailable",service(true).lookup("10.1234/test").lookupStatus);
    }
    @Test void atMostTwoActualLookupsRunConcurrently() throws Exception {
        CountDownLatch entered=new CountDownLatch(2),release=new CountDownLatch(1);
        when(client.lookup(anyString())).thenAnswer(call->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return record(call.getArgument(0));});
        DoiMetadataService service=service(true);ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> first=pool.submit(()->service.lookup("10.1234/one")),second=pool.submit(()->service.lookup("10.1234/two"));
            assertTrue(entered.await(3,TimeUnit.SECONDS));assertEquals("busy",service.lookup("10.1234/three").lookupStatus);
            release.countDown();first.get(3,TimeUnit.SECONDS);second.get(3,TimeUnit.SECONDS);
            assertEquals("found",service.lookup("10.1234/three").lookupStatus);
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void createdTimeAndFuturePublicationDatesDoNotBecomeThePublicationYear() throws Exception {
        ObjectNode record=record("10.1234/test");record.putObject("published-print").putArray("date-parts").addArray().add(2099);
        record.putObject("created").put("date-time","2020-01-01");record.putObject("published-online").putArray("date-parts").addArray().add(2024);
        when(client.lookup(anyString())).thenReturn(record);assertEquals(2024,service(true).lookup("10.1234/test").year);
    }
    @Test void htmlScriptsAndEntitiesAreTextOnlyWithoutLoadingExternalDocuments() throws Exception {
        String text=MetadataPlainText.read("<!DOCTYPE html SYSTEM 'http://127.0.0.1:9/external'><p>Safe &lt;result&gt; &#945;</p><script>bad()</script><style>secret</style><p>Next</p>");
        assertTrue(text.contains("Safe <result> α"));assertFalse(text.contains("bad()"));assertFalse(text.contains("secret"));assertTrue(text.contains("Next"));
    }
}
