package cn.paperhub.paper;

import cn.paperhub.paper.service.PaperMetadataService;
import cn.paperhub.support.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.file.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="paperhub.storage.cleanup-enabled=false")
@AutoConfigureMockMvc
class PaperMetadataIT extends SharingSupport {
    ResultActions parse(byte[] bytes,String token) throws Exception {
        return mvc.perform(multipart("/api/v1/papers/metadata/parse")
                .file(new MockMultipartFile("file","paper.pdf","application/pdf",bytes))
                .header("Authorization",token));
    }
    @Test void authenticatedParseReturnsFieldsWithoutCreatingPapersRecordsOrPersistentPdfFiles() throws Exception {
        long papers=value("SELECT COUNT(*) FROM paper"),logs=value("SELECT COUNT(*) FROM operation_log");
        long files;try(java.util.stream.Stream<Path> list=Files.list(storage.root().resolve("papers"))) { files=list.count(); }
        JsonNode result=ok(parse(MetadataPdf.paper(true),user));
        assertEquals(MetadataPdf.TITLE,result.path("title").asText());assertEquals("John Smith",result.path("authors").get(0).asText());
        assertEquals("10.1234/local.parse",result.path("doi").asText());assertEquals(2025,result.path("year").asInt());
        assertEquals(1,result.path("pageCount").asInt());assertFalse(result.has("filePath"));assertFalse(result.has("id"));
        assertEquals(papers,value("SELECT COUNT(*) FROM paper"));assertEquals(logs,value("SELECT COUNT(*) FROM operation_log"));
        try(java.util.stream.Stream<Path> list=Files.list(storage.root().resolve("papers"))) { assertEquals(files,list.count()); }
        try(java.util.stream.Stream<Path> list=Files.list(storage.root().resolve(".scratch"))) { assertEquals(0,list.count()); }
        // Only the verified disposable test root receives fixtures for executable-JAR/browser verification.
        Files.write(storage.root().getParent().resolve("metadata-fixture.pdf"),MetadataPdf.paper(false));
        Files.write(storage.root().getParent().resolve("metadata-image-fixture.pdf"),pdf());
    }
    @Test void anonymousExpiredAndDisabledSessionsCannotUseTheParser() throws Exception {
        mvc.perform(multipart("/api/v1/papers/metadata/parse").file(new MockMultipartFile("file",MetadataPdf.paper(true))))
                .andExpect(status().isUnauthorized());
        parse(MetadataPdf.paper(true),"Bearer invalid").andExpect(status().isUnauthorized());
        jdbc.update("UPDATE `user` SET status='disabled' WHERE id=1");
        parse(MetadataPdf.paper(true),user).andExpect(status().isUnauthorized());
    }
    @Test void administratorCanParseAnUploadedFileUsingTheSameTransientContract() throws Exception {
        parse(MetadataPdf.paper(true),admin).andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value(MetadataPdf.TITLE));
    }
    @Test void missingEmptyCorruptAndNonPdfFilesReturnUnifiedErrors() throws Exception {
        mvc.perform(multipart("/api/v1/papers/metadata/parse").header("Authorization",user)).andExpect(status().isUnprocessableEntity());
        for(byte[] data:new byte[][]{new byte[0],"not a PDF".getBytes("UTF-8"),"%PDF-broken".getBytes("UTF-8")})
            parse(data,user).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(1005));
    }
    @Test void encryptedAndOversizedPdfsAreRejectedWithoutPersistingAResource() throws Exception {
        byte[] bytes;
        try(PDDocument document=PDDocument.load(MetadataPdf.paper(true));ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            StandardProtectionPolicy policy=new StandardProtectionPolicy("owner","secret",new AccessPermission());
            document.protect(policy);document.save(output);bytes=output.toByteArray();
        }
        parse(bytes,user).andExpect(status().isUnprocessableEntity());
        MockMultipartFile large=mock(MockMultipartFile.class);
        when(large.getName()).thenReturn("file");when(large.getSize()).thenReturn(PaperMetadataService.LIMIT+1);
        mvc.perform(multipart("/api/v1/papers/metadata/parse").file(large).header("Authorization",user))
                .andExpect(status().isUnprocessableEntity());
        verify(large,never()).getInputStream();
    }
    @Test void imageOnlyPdfReturnsManualEntryWarningsRatherThanFabricatedAbstract() throws Exception {
        JsonNode value=ok(parse(pdf(),user));assertEquals("",value.path("abstract").asText());
        assertTrue(value.path("authors").isEmpty());assertTrue(value.path("warnings").toString().contains("OCR"));
    }
}
