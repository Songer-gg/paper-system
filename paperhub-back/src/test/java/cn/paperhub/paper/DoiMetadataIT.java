package cn.paperhub.paper;

import cn.paperhub.paper.service.CrossrefClient;
import cn.paperhub.support.SharingSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"paperhub.metadata.crossref-enabled=true","paperhub.storage.cleanup-enabled=false"})
@AutoConfigureMockMvc
class DoiMetadataIT extends SharingSupport {
    @MockBean CrossrefClient client;
    @BeforeEach void providerFixture() throws Exception {
        reset(client);
        when(client.lookup(anyString())).thenAnswer(call->body("DOI",call.getArgument(0),"title",new String[]{"Exact DOI title"},
                "abstract","<jats:p>Original provider abstract &amp; results.</jats:p>","author",new Object[]{body("given","John","family","Smith")},
                "published",body("date-parts",new int[][]{{2025}})));
    }
    @Test void authenticatedExactDoiLookupReturnsPlainTextWithoutWritingBusinessData() throws Exception {
        long papers=value("SELECT COUNT(*) FROM paper"),logs=value("SELECT COUNT(*) FROM operation_log");
        JsonNode result=ok(api("POST","/papers/metadata/lookup",body("doi","https://doi.org/10.1234/"+unique()),user));
        assertEquals("found",result.path("lookupStatus").asText());assertEquals("Original provider abstract & results.",result.path("abstract").asText());
        assertEquals("John Smith",result.path("authors").get(0).asText());assertEquals("crossref",result.path("sources").path("abstract").asText());
        assertEquals(papers,value("SELECT COUNT(*) FROM paper"));assertEquals(logs,value("SELECT COUNT(*) FROM operation_log"));
        assertFalse(result.has("filePath"));assertFalse(result.has("userId"));
    }
    @Test void anonymousExpiredAndDisabledAccountsCannotQueryTheProvider() throws Exception {
        api("POST","/papers/metadata/lookup",body("doi","10.1234/test"),null).andExpect(status().isUnauthorized());
        api("POST","/papers/metadata/lookup",body("doi","10.1234/test"),"Bearer invalid").andExpect(status().isUnauthorized());
        jdbc.update("UPDATE `user` SET status='disabled' WHERE id=1");
        api("POST","/papers/metadata/lookup",body("doi","10.1234/test"),user).andExpect(status().isUnauthorized());verifyNoInteractions(client);
    }
    @Test void invalidDoiAndUnknownBodyFieldsAreRejectedBeforeAnyProviderCall() throws Exception {
        for(String doi:new String[]{"","https://localhost/internal","10.12/not-valid"})
            api("POST","/papers/metadata/lookup",body("doi",doi),user).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(1005));
        api("POST","/papers/metadata/lookup",body("doi","10.1234/test","url","http://localhost/private"),user).andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(client);
    }
    @Test void absentRecordAndNetworkFailureRemainSuccessfulSoftFailureResponses() throws Exception {
        when(client.lookup(anyString())).thenReturn(null);
        api("POST","/papers/metadata/lookup",body("doi","10.1234/absent-"+unique()),user).andExpect(status().isOk()).andExpect(jsonPath("$.data.lookupStatus").value("not-found"));
        when(client.lookup(anyString())).thenThrow(new IOException("upstream-private-probe"));
        String response=api("POST","/papers/metadata/lookup",body("doi","10.1234/failure-"+unique()),user).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lookupStatus").value("unavailable")).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("upstream-private-probe"));
    }
    @Test void providerMissingAbstractDoesNotTurnTheTitleIntoAnAbstract() throws Exception {
        when(client.lookup(anyString())).thenAnswer(call->body("DOI",call.getArgument(0),"title",new String[]{"Not an abstract"}));
        api("POST","/papers/metadata/lookup",body("doi","10.1234/no-abstract-"+unique()),user).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Not an abstract")).andExpect(jsonPath("$.data.abstract").value(""));
    }
    @Test void administratorCanUseTheSameTransientContract() throws Exception {
        api("POST","/papers/metadata/lookup",body("doi","10.1234/admin-"+unique()),admin).andExpect(status().isOk()).andExpect(jsonPath("$.data.lookupStatus").value("found"));
    }
}
