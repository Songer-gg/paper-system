package cn.paperhub.paper;

import cn.paperhub.audit.mapper.AuditMapper;
import cn.paperhub.auth.mapper.UserAccountMapper;
import cn.paperhub.common.storage.*;
import cn.paperhub.paper.service.PaperService;
import cn.paperhub.security.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="paperhub.storage.cleanup-enabled=false")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaperIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired UserAccountMapper users;
    @Autowired PaperService papers;
    @Autowired FileLifecycle lifecycle;
    @Autowired PlatformTransactionManager transactions;
    @SpyBean AuditMapper audit;
    @SpyBean LocalFileStorage storage;
    String user, other, admin;

    @BeforeAll void isolatedDatabaseOnly() {
        String expected=System.getenv("PAPERHUB_TEST_DATA_DIR");
        assertNotNull(expected,"Run scripts/verify_mysql.py, never against the existing local database");
        assertEquals(Paths.get(expected).toAbsolutePath().normalize(),
                Paths.get(jdbc.queryForObject("SELECT @@datadir",String.class)).toAbsolutePath().normalize());
        assertTrue(storage.root().startsWith(Paths.get(expected).toAbsolutePath().getParent()));
        jdbc.update("INSERT INTO category(id,name,custom,owner_id) VALUES(71001,'test owner 1',1,1),(71002,'test owner 2',1,2)");
        jdbc.update("INSERT INTO tag(id,name,color,custom,owner_id) VALUES(71001,'test own tag 1','#000000',1,1),(71002,'test own tag 2','#000000',1,2)");
    }
    @BeforeEach void tokens() {
        jdbc.update("UPDATE `user` SET status='active',role='user',token_version=0 WHERE id IN (1,2)");
        jdbc.update("UPDATE `user` SET status='active',role='admin',token_version=0 WHERE id=101");
        user="Bearer "+jwt.issue(users.selectById(1));
        other="Bearer "+jwt.issue(users.selectById(2));
        admin="Bearer "+jwt.issue(users.selectById(101));
    }
    @AfterEach void resetSpies() { reset(audit,storage); }

    byte[] pdf(int pages) throws IOException {
        try(PDDocument doc=new PDDocument(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            for(int i=0;i<pages;i++) doc.addPage(new PDPage());
            doc.getDocumentInformation().setTitle(UUID.randomUUID().toString());
            doc.save(output); return output.toByteArray();
        }
    }
    ObjectNode payload(String visibility) {
        ObjectNode p=json.createObjectNode();
        p.put("title","test "+UUID.randomUUID()); p.putArray("authors").add("作者 A").add("Author B");
        p.put("abstract","测试摘要"); p.put("year",2026); p.put("categoryId",1);
        p.putArray("tagIds").add(1).add(2); p.putArray("keywords").add("测试");
        p.put("visibility",visibility); p.put("copyrightAgreed",visibility.equals("shared"));
        return p;
    }
    ResultActions upload(ObjectNode body, byte[] file, String token) throws Exception {
        return mvc.perform(multipart("/api/v1/papers")
                .file(new MockMultipartFile("file","../../evil.pdf","application/pdf",file))
                .file(new MockMultipartFile("payload","payload.json","application/json",json.writeValueAsBytes(body)))
                .header("Authorization",token));
    }
    JsonNode uploaded(ObjectNode body, byte[] file, String token) throws Exception {
        return json.readTree(upload(body,file,token).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
    long uploadPrivate() throws Exception { return uploaded(payload("private"),pdf(2),user).path("id").asLong(); }
    String key(long id) { return jdbc.queryForObject("SELECT file_path FROM paper WHERE id=?",String.class,id); }
    long countFiles(String dir) throws IOException {
        try(Stream<Path> paths=Files.list(storage.root().resolve(dir))) { return paths.count(); }
    }
    long logs(long id, String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action=? AND target LIKE ?",Long.class,action,"paperId="+id+";%");
    }

    @Test void uploadComputesRealMetadataAndMapsTheFrontendContract() throws Exception {
        byte[] bytes=pdf(2);
        JsonNode paper=uploaded(payload("private"),bytes,user);
        long id=paper.path("id").asLong();
        assertEquals(2,paper.path("pageCount").asInt());
        assertEquals("林知远",paper.path("uploaderName").asText());
        assertEquals("测试摘要",paper.path("abstract").asText());
        assertEquals(2,paper.path("authors").size());
        assertEquals("",paper.path("doi").asText()); assertEquals("",paper.path("journal").asText());
        assertFalse(paper.has("reviewStatus")); assertFalse(paper.has("abstractText"));
        assertTrue(paper.has("fileSizeMB")); assertFalse(paper.has("fileSizeMb"));
        for(String hidden:Arrays.asList("filePath","fileSha256","deletedAt","version","doiNormalized","password"))
            assertFalse(paper.has(hidden),hidden);
        StringBuilder hash=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes)) hash.append(String.format("%02x",b&255));
        assertEquals(hash.toString(),jdbc.queryForObject("SELECT file_sha256 FROM paper WHERE id=?",String.class,id));
        assertTrue(key(id).matches("papers/[0-9a-f-]{36}\\.pdf"));
        assertArrayEquals(bytes,Files.readAllBytes(storage.resolve(key(id))));
        assertEquals(1,logs(id,"论文上传"));
    }
    @Test void ordinaryUsersCannotAccessOrMutateAnotherPrivatePaper() throws Exception {
        long id=uploadPrivate();
        mvc.perform(get("/api/v1/papers/"+id).header("Authorization",other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/papers/"+id+"/file").header("Authorization",other)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/papers/"+id+"/download").header("Authorization",other)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/papers/"+id).header("Authorization",other).contentType("application/json").content("{\"title\":\"steal\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/papers/"+id).header("Authorization",other)).andExpect(status().isNotFound());
        JsonNode mine=json.readTree(mvc.perform(get("/api/v1/papers/mine").param("userId","1").header("Authorization",other))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        for(JsonNode item:mine.path("privatePapers")) assertNotEquals(id,item.path("id").asLong());
        jdbc.update("UPDATE `user` SET status='disabled' WHERE id=1");
        mvc.perform(get("/api/v1/papers/"+id).header("Authorization",user)).andExpect(status().isUnauthorized());
    }
    @Test void previewDoesNotCountAndConcurrentDownloadsCountAtomically() throws Exception {
        long id=uploadPrivate();
        byte[] expected=Files.readAllBytes(storage.resolve(key(id)));
        mvc.perform(get("/api/v1/papers/"+id+"/file").header("Authorization",user))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(content().bytes(expected));
        assertEquals(0,jdbc.queryForObject("SELECT downloads FROM paper WHERE id=?",Integer.class,id));
        ExecutorService pool=Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> calls=new ArrayList<>();
            for(int i=0;i<18;i++) calls.add(()->mvc.perform(post("/api/v1/papers/"+id+"/download").header("Authorization",user))
                    .andExpect(content().bytes(expected)).andReturn().getResponse().getStatus());
            for(Future<Integer> result:pool.invokeAll(calls)) assertEquals(200,result.get().intValue());
        } finally { pool.shutdownNow(); }
        assertEquals(18,jdbc.queryForObject("SELECT downloads FROM paper WHERE id=?",Integer.class,id));
    }
    @Test void adminReadsPrivateDataAndLeavesIndependentAuditEvenWhenOuterTransactionRollsBack() throws Exception {
        long id=uploadPrivate();
        mvc.perform(get("/api/v1/papers/"+id).header("Authorization",admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/papers/"+id+"/file").header("Authorization",admin)).andExpect(status().isOk());
        long before=logs(id,"敏感数据访问");
        assertTrue(before>=2);
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status->{
            papers.detail(id,new SessionPrincipal(101,"admin","admin")); throw new IllegalStateException("rollback outer");
        }));
        assertEquals(before+1,logs(id,"敏感数据访问"));
        Map<String,Object> log=jdbc.queryForMap("SELECT * FROM operation_log WHERE action='敏感数据访问' AND target LIKE ? ORDER BY id DESC LIMIT 1","paperId="+id+";%");
        assertEquals(101L,((Number)log.get("user_id")).longValue());
        assertTrue(log.get("target").toString().contains("ownerId=1"));
        assertFalse(log.toString().contains("测试摘要"));
    }
    @Test void auditOutageRejectsMetadataAndPdfAndDoesNotIncrementDownloads() throws Exception {
        long id=uploadPrivate();
        doThrow(new DataAccessResourceFailureException("simulated audit outage")).when(audit)
                .append(eq(101L),eq("敏感数据访问"),anyString(),anyString(),any());
        mvc.perform(get("/api/v1/papers/"+id).header("Authorization",admin))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(1500));
        mvc.perform(post("/api/v1/papers/"+id+"/download").header("Authorization",admin))
                .andExpect(status().isInternalServerError()).andExpect(content().contentTypeCompatibleWith("application/json"));
        assertEquals(0,jdbc.queryForObject("SELECT downloads FROM paper WHERE id=?",Integer.class,id));
        mvc.perform(get("/api/v1/papers/"+id+"/file").header("Authorization",user)).andExpect(status().isOk());
    }
    @Test void databaseFailureRollsBackPaperTagsAndStoredFile() throws Exception {
        long filesBefore=countFiles("papers"), jobsBefore=countFiles(".cleanup");
        ObjectNode body=payload("private");
        doThrow(new DataAccessResourceFailureException("simulated write outage")).when(audit)
                .append(eq(1L),eq("论文上传"),anyString(),anyString(),any());
        upload(body,pdf(1),user).andExpect(status().isInternalServerError());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM paper WHERE title=?",Integer.class,body.path("title").asText()));
        assertEquals(filesBefore,countFiles("papers")); assertEquals(jobsBefore,countFiles(".cleanup"));
    }
    @Test void softDeleteKeepsNotesAndHistoriesAndCleanupRetriesAcrossWorkerRestart() throws Exception {
        long id=uploadPrivate(); String key=key(id); Path path=storage.resolve(key);
        jdbc.update("INSERT INTO note(user_id,paper_id,paper_title,title,content) VALUES(2,?,'snapshot','note','private notes')",id);
        jdbc.update("INSERT INTO favorite(user_id,paper_id) VALUES(2,?)",id);
        jdbc.update("INSERT INTO reading_record(user_id,paper_id) VALUES(2,?)",id);
        jdbc.update("INSERT INTO review_record(paper_id,reviewer_id,paper_title,uploader_id,action) VALUES(?,101,'snapshot',1,'rejected')",id);
        doThrow(new IOException("simulated locked file")).when(storage).delete(key);
        mvc.perform(delete("/api/v1/papers/"+id).header("Authorization",user)).andExpect(status().isOk());
        assertTrue(Files.exists(path));
        for(String table:Arrays.asList("note","favorite","reading_record","review_record"))
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE paper_id=?",Integer.class,id));
        mvc.perform(get("/api/v1/papers/"+id+"/file").header("Authorization",admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/papers/"+id).header("Authorization",user)).andExpect(status().isNotFound());
        reset(storage);
        try(Stream<Path> jobs=Files.list(storage.root().resolve(".cleanup"))) {
            for(Path job:jobs.collect(Collectors.toList())) Files.setLastModifiedTime(job,FileTime.from(Instant.now().minusSeconds(601)));
        }
        new FileLifecycle(storage,jdbc,transactions).retryPending();
        assertFalse(Files.exists(path));
        assertEquals(0,countFiles(".cleanup"));
    }
    @Test void privateDuplicatesAreAllowedButSharedHashesAndNormalizedDoisAreUnique() throws Exception {
        byte[] same=pdf(1);
        uploaded(payload("private"),same,user); uploaded(payload("private"),same,other);
        ObjectNode first=payload("shared"); String doi="10.9999/"+UUID.randomUUID();
        first.put("doi","https://doi.org/"+doi.toUpperCase(Locale.ROOT));
        JsonNode paper=uploaded(first,same,user);
        assertEquals("pending",paper.path("reviewStatus").asText());
        long before=countFiles("papers");
        ObjectNode second=payload("shared"); second.put("doi","doi: "+doi);
        upload(second,pdf(1),other).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(1004));
        upload(payload("shared"),same,other).andExpect(status().isConflict());
        assertEquals(before,countFiles("papers"));
        assertEquals(doi,jdbc.queryForObject("SELECT doi_normalized FROM paper WHERE id=?",String.class,paper.path("id").asLong()));
    }
    @Test void simultaneousSharedUploadsLeaveExactlyOneFileAndOnePaper() throws Exception {
        byte[] same=pdf(1);
        ObjectNode one=payload("shared"),two=payload("shared");
        long before=countFiles("papers");
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> calls=pool.invokeAll(Arrays.asList(
                    ()->upload(one,same,user).andReturn().getResponse().getStatus(),
                    ()->upload(two,same,other).andReturn().getResponse().getStatus()));
            List<Integer> codes=new ArrayList<>();
            for(Future<Integer> call:calls) codes.add(call.get());
            Collections.sort(codes); assertEquals(Arrays.asList(200,409),codes);
        } finally { pool.shutdownNow(); }
        assertEquals(before+1,countFiles("papers"));
    }
    @Test void titleMatchesCreatePendingEvidenceWithoutBlockingDifferentFiles() throws Exception {
        ObjectNode first=payload("shared");
        uploaded(first,pdf(1),user);
        ObjectNode second=payload("shared"); second.put("title",first.path("title").asText());
        long id=uploaded(second,pdf(1),other).path("id").asLong();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM duplicate_pair WHERE new_paper_id=? AND status='pending' AND title_similarity=100",Integer.class,id));
    }
    @Test void metadataEditsClearNullableFieldsAndRequeueApprovedSharedPapers() throws Exception {
        ObjectNode body=payload("shared"); body.put("doi","10.9999/"+UUID.randomUUID());
        long id=uploaded(body,pdf(1),user).path("id").asLong();
        jdbc.update("UPDATE paper SET review_status='approved',review_comment='old' WHERE id=?",id);
        mvc.perform(patch("/api/v1/papers/"+id).header("Authorization",user).contentType("application/json")
                .content("{\"title\":\""+body.path("title").asText()+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reviewStatus").value("approved"));
        mvc.perform(patch("/api/v1/papers/"+id).header("Authorization",admin).contentType("application/json")
                .content("{\"categoryId\":null,\"doi\":\"\",\"tagIds\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reviewStatus").value("pending"))
                .andExpect(jsonPath("$.data.categoryId").isEmpty()).andExpect(jsonPath("$.data.reviewComment").doesNotExist());
        assertNull(jdbc.queryForObject("SELECT category_id FROM paper WHERE id=?",Long.class,id));
        assertNull(jdbc.queryForObject("SELECT doi_normalized FROM paper WHERE id=?",String.class,id));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM paper_tag WHERE paper_id=?",Integer.class,id));
    }
    @Test void sharedEditConflictRollsBackMetadataAndAssociations() throws Exception {
        ObjectNode first=payload("shared"); first.put("doi","10.9999/"+UUID.randomUUID());
        uploaded(first,pdf(1),user);
        long second=uploaded(payload("shared"),pdf(1),user).path("id").asLong();
        jdbc.update("UPDATE paper SET review_status='approved' WHERE id=?",second);
        mvc.perform(patch("/api/v1/papers/"+second).header("Authorization",user).contentType("application/json")
                .content("{\"doi\":\""+first.path("doi").asText()+"\",\"tagIds\":[]}"))
                .andExpect(status().isConflict());
        assertEquals("approved",jdbc.queryForObject("SELECT review_status FROM paper WHERE id=?",String.class,second));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM paper_tag WHERE paper_id=?",Integer.class,second));
    }
    @Test void classifiedResourcesRespectOwnershipAndSharedPlatformScope() throws Exception {
        ObjectNode own=payload("private"); own.put("categoryId",71001); own.putArray("tagIds").add(71001);
        uploaded(own,pdf(1),user);
        upload(own,pdf(1),other).andExpect(status().isUnprocessableEntity());
        own.put("visibility","shared"); own.put("copyrightAgreed",true);
        upload(own,pdf(1),user).andExpect(status().isUnprocessableEntity());
        own=payload("private"); own.putArray("tagIds").add(71002);
        upload(own,pdf(1),user).andExpect(status().isUnprocessableEntity());
    }
    @Test void publicSharedPapersAreReadableButNotEditableByAnotherUser() throws Exception {
        long id=uploaded(payload("shared"),pdf(1),user).path("id").asLong();
        mvc.perform(get("/api/v1/papers/"+id).header("Authorization",other)).andExpect(status().isNotFound());
        jdbc.update("UPDATE paper SET review_status='approved',review_comment='internal comment' WHERE id=?",id);
        mvc.perform(get("/api/v1/papers/"+id).header("Authorization",other)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewComment").doesNotExist());
        mvc.perform(get("/api/v1/papers/"+id+"/file").header("Authorization",other)).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/papers/"+id).header("Authorization",other).contentType("application/json").content("{\"title\":\"hack\"}"))
                .andExpect(status().isForbidden());
    }
    @Test void uploadAndPatchRejectPrivilegeFieldsAndMalformedArrayValues() throws Exception {
        ObjectNode p=payload("private"); p.put("uploaderId",2);
        upload(p,pdf(1),user).andExpect(status().isUnprocessableEntity());
        p=payload("private"); p.putArray("authors").addObject().put("name","invalid");
        upload(p,pdf(1),user).andExpect(status().isUnprocessableEntity());
        p=payload("private"); p.putArray("authors").add(123);
        upload(p,pdf(1),user).andExpect(status().isUnprocessableEntity());
        p=payload("private"); p.put("year",2026.5);
        upload(p,pdf(1),user).andExpect(status().isUnprocessableEntity());
        long id=uploadPrivate();
        for(String invalid:Arrays.asList("{}","{\"downloads\":100}","{\"visibility\":\"shared\"}","{\"reviewStatus\":\"approved\"}","{\"authors\":null}","{\"title\":null}","{\"link\":\"javascript:alert(1)\"}"))
            mvc.perform(patch("/api/v1/papers/"+id).header("Authorization",user).contentType("application/json").content(invalid))
                    .andExpect(status().isUnprocessableEntity());
    }
    @Test void malformedEncryptedAndMissingFilesFailWithoutLeavingStorage() throws Exception {
        long count=countFiles("papers");
        upload(payload("private"),"%PDF-invalid".getBytes(StandardCharsets.US_ASCII),user).andExpect(status().isUnprocessableEntity());
        upload(payload("private"),"pretend image".getBytes(StandardCharsets.US_ASCII),user).andExpect(status().isUnprocessableEntity());
        try(PDDocument doc=new PDDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.protect(new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("owner","secret",new org.apache.pdfbox.pdmodel.encryption.AccessPermission()));
            doc.save(out);
            upload(payload("private"),out.toByteArray(),user).andExpect(status().isUnprocessableEntity());
        }
        mvc.perform(multipart("/api/v1/papers").header("Authorization",user)).andExpect(status().isUnprocessableEntity());
        assertEquals(count,countFiles("papers"));
        long id=uploadPrivate(); Files.delete(storage.resolve(key(id)));
        mvc.perform(post("/api/v1/papers/"+id+"/download").header("Authorization",admin))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(1003));
        assertEquals(0,jdbc.queryForObject("SELECT downloads FROM paper WHERE id=?",Integer.class,id));
        assertEquals("失败",jdbc.queryForObject("SELECT result FROM operation_log WHERE action='敏感数据访问' AND target LIKE ? ORDER BY id DESC LIMIT 1",
                String.class,"paperId="+id+";%"));
    }
    @Test void tamperedStoragePathsCannotReadOutsideStorage() throws Exception {
        long id=uploadPrivate();
        jdbc.update("UPDATE paper SET file_path='../pom.xml' WHERE id=?",id);
        mvc.perform(get("/api/v1/papers/"+id+"/file").header("Authorization",user)).andExpect(status().isNotFound());
    }
    @Test void workerDoesNotDeleteACommittedUploadWithAnUncancelledCrashMarker() throws Exception {
        long id=uploadPrivate(); String key=key(id); String job=storage.queue(key);
        Files.setLastModifiedTime(storage.jobPath(job),FileTime.from(Instant.now().minusSeconds(601)));
        new FileLifecycle(storage,jdbc,transactions).retryPending();
        assertTrue(Files.exists(storage.resolve(key)));
        assertFalse(Files.exists(storage.jobPath(job)));
    }
    byte[] png(int width) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width,1,BufferedImage.TYPE_INT_RGB),"png",out); return out.toByteArray();
    }
    ResultActions avatar(byte[] bytes,String token) throws Exception {
        MockMultipartHttpServletRequestBuilder builder=multipart("/api/v1/me/avatar");
        builder.with(request->{request.setMethod("PUT");return request;});
        return mvc.perform(builder.file(new MockMultipartFile("file","../../avatar.svg","image/svg+xml",bytes))
                .header("Authorization",token));
    }
    @Test void avatarIsDecodedStoredAsRandomPngAndReturnedWithoutAnExtraPublicEndpoint() throws Exception {
        byte[] bytes=png(64);
        JsonNode response=json.readTree(avatar(bytes,user).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1)).andExpect(jsonPath("$.data.role").value("user"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertTrue(response.path("avatar").asText().startsWith("data:image/png;base64,"));
        assertFalse(response.has("password")); assertTrue(response.has("uploadedCount"));
        String old=jdbc.queryForObject("SELECT avatar FROM `user` WHERE id=1",String.class);
        assertTrue(old.matches("avatars/[0-9a-f-]{36}\\.png")); assertTrue(old.length()<255);
        assertNotNull(ImageIO.read(storage.resolve(old).toFile()));
        avatar(bytes,user).andExpect(status().isOk());
        assertFalse(Files.exists(storage.resolve(old)));
    }
    @Test void avatarRejectsOversizeInvalidAndExcessiveDimensions() throws Exception {
        avatar("<svg/>".getBytes(StandardCharsets.US_ASCII),user).andExpect(status().isUnprocessableEntity());
        avatar(png(4096),user).andExpect(status().isUnprocessableEntity());
        avatar(new byte[2*1024*1024+1],user).andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart("/api/v1/me/avatar").with(request->{request.setMethod("PUT");return request;}))
                .andExpect(status().isUnauthorized());
    }
}

