package cn.paperhub.support;

import cn.paperhub.admin.mapper.*;
import cn.paperhub.audit.mapper.AuditMapper;
import cn.paperhub.auth.mapper.UserAccountMapper;
import cn.paperhub.common.storage.LocalFileStorage;
import cn.paperhub.security.JwtService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class SharingSupport {
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected JwtService jwt;
    @Autowired protected UserAccountMapper users;
    @Autowired protected LocalFileStorage storage;
    @SpyBean protected AuditMapper audit;
    @SpyBean protected ReviewRecordMapper reviews;
    @SpyBean protected DuplicatePairMapper pairs;
    protected String user,other,admin;
    @BeforeAll void isolatedOnly() {
        String expected=System.getenv("PAPERHUB_TEST_DATA_DIR");
        assertNotNull(expected,"Use scripts/verify_mysql.py; never mutate the existing development database");
        assertEquals(Paths.get(expected).toAbsolutePath().normalize(),
                Paths.get(jdbc.queryForObject("SELECT @@datadir",String.class)).toAbsolutePath().normalize());
        assertTrue(storage.root().startsWith(Paths.get(expected).toAbsolutePath().getParent()));
    }
    @BeforeEach void tokens() {
        jdbc.update("UPDATE `user` SET role='user',status='active',token_version=0 WHERE id IN (1,2)");
        jdbc.update("UPDATE `user` SET role='admin',status='active',token_version=0 WHERE id=101");
        user="Bearer "+jwt.issue(users.selectById(1)); other="Bearer "+jwt.issue(users.selectById(2));
        admin="Bearer "+jwt.issue(users.selectById(101));
    }
    @AfterEach void clearSpies() { reset(audit,reviews,pairs); }
    protected String unique() { return UUID.randomUUID().toString(); }
    protected ObjectNode body(Object... fields) {
        ObjectNode result=json.createObjectNode();
        for(int i=0;i<fields.length;i+=2) result.set((String)fields[i],json.valueToTree(fields[i+1]));
        return result;
    }
    protected ObjectNode base(String visibility) {
        ObjectNode result=body("title","Resource "+unique(),"abstract","Sharing abstract "+unique(),"year",2026,
                "categoryId",1,"visibility",visibility,"copyrightAgreed","shared".equals(visibility));
        result.putArray("authors").add("Research author");
        result.putArray("keywords").add("research"); result.putArray("tagIds");
        return result;
    }
    protected ResultActions api(String method,String path,JsonNode data,String token) throws Exception {
        MockHttpServletRequestBuilder request=request(HttpMethod.valueOf(method),"/api/v1"+path);
        if(token!=null) request.header("Authorization",token);
        if(data!=null) request.contentType("application/json").content(json.writeValueAsBytes(data));
        return mvc.perform(request);
    }
    protected JsonNode ok(ResultActions actions) throws Exception {
        return json.readTree(actions.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
    protected JsonNode read(String path,String token) throws Exception { return ok(api("GET",path,null,token)); }
    protected byte[] pdf() throws IOException {
        try(PDDocument document=new PDDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.getDocumentInformation().setTitle(unique());
            document.save(out); return out.toByteArray();
        }
    }
    protected ResultActions upload(ObjectNode payload,byte[] file,String token) throws Exception {
        return mvc.perform(multipart("/api/v1/papers")
                .file(new MockMultipartFile("payload","payload.json","application/json",json.writeValueAsBytes(payload)))
                .file(new MockMultipartFile("file","test.pdf","application/pdf",file)).header("Authorization",token));
    }
    protected long paper(String visibility,String token) throws Exception { return create(base(visibility),token); }
    protected long create(ObjectNode payload,String token) throws Exception { return ok(upload(payload,pdf(),token)).path("id").asLong(); }
    protected long titled(String title,String token) throws Exception {
        ObjectNode payload=base("shared"); payload.put("title",title); return create(payload,token);
    }
    protected ResultActions approve(long id) throws Exception {
        return api("POST","/admin/review/papers/"+id+"/approve",body("comment","reviewed","copyrightConfirmed",true),admin);
    }
    protected long publish(ObjectNode payload,String token) throws Exception { long id=create(payload,token); ok(approve(id)); return id; }
    protected ResultActions flow(long id,String action,Boolean consent,String token) throws Exception {
        ObjectNode input=body("action",action);
        if(consent!=null) input.put("copyrightAgreed",consent);
        return api("PATCH","/papers/"+id+"/review-flow",input,token);
    }
    protected long value(String sql,Object... args) { return jdbc.queryForObject(sql,Long.class,args); }
    protected String state(long id) { return jdbc.queryForObject("SELECT review_status FROM paper WHERE id=?",String.class,id); }
    protected long pair(long first,long second) {
        return value("SELECT id FROM duplicate_pair WHERE (original_paper_id=? AND new_paper_id=?) "+
                "OR (original_paper_id=? AND new_paper_id=?)",first,second,second,first);
    }
    protected String key(long id) { return jdbc.queryForObject("SELECT file_path FROM paper WHERE id=?",String.class,id); }
    protected JsonNode named(JsonNode list,long id) {
        for(JsonNode item:list) if(item.path("id").asLong()==id) return item;
        fail("Missing id="+id); return null;
    }
    protected Set<Long> ids(JsonNode list) {
        Set<Long> result=new HashSet<>();
        for(JsonNode item:list) result.add(item.path("id").asLong());
        return result;
    }
    protected void freeLock() {
        assertEquals(1,value("SELECT IS_FREE_LOCK(?)",cn.paperhub.paper.service.SharedCatalogLock.NAME));
    }
    protected JsonNode search(String... params) throws Exception {
        MockHttpServletRequestBuilder request=get("/api/v1/shared-papers").header("Authorization",user);
        for(int i=0;i<params.length;i+=2) request.param(params[i],params[i+1]);
        return ok(mvc.perform(request));
    }
}

