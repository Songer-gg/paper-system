package cn.paperhub.knowledge;

import cn.paperhub.audit.mapper.AuditMapper;
import cn.paperhub.auth.mapper.UserAccountMapper;
import cn.paperhub.common.storage.LocalFileStorage;
import cn.paperhub.knowledge.mapper.KnowledgeRelationMapper;
import cn.paperhub.knowledge.service.*;
import cn.paperhub.security.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
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
class KnowledgeIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired UserAccountMapper users;
    @Autowired LocalFileStorage storage;
    @Autowired CategoryService categories;
    @Autowired PlatformTransactionManager transactions;
    @SpyBean AuditMapper audit;
    @SpyBean KnowledgeRelationMapper relations;
    @SpyBean KnowledgeLocks locks;
    String user,other,admin;

    @BeforeAll void requireIsolatedDatabase() {
        String expected=System.getenv("PAPERHUB_TEST_DATA_DIR");
        assertNotNull(expected,"Use scripts/verify_mysql.py; do not run against the existing development DB");
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
    @AfterEach void resetSpies() { reset(audit,relations,locks); }

    String unique() { return "K "+UUID.randomUUID(); }
    ObjectNode node(String name,Long parent) {
        ObjectNode body=json.createObjectNode().put("name",name);
        if(parent==null) body.putNull("parentId"); else body.put("parentId",parent);
        return body;
    }
    JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
    ResultActions createCategory(String name,Long parent,String actor) throws Exception {
        return mvc.perform(post("/api/v1/categories").header("Authorization",actor).contentType("application/json")
                .content(json.writeValueAsBytes(node(name,parent))));
    }
    long category(Long parent,String actor) throws Exception {
        return data(createCategory(unique(),parent,actor)).path("id").asLong();
    }
    ResultActions createTag(String name,String actor) throws Exception {
        return mvc.perform(post("/api/v1/tags").header("Authorization",actor).contentType("application/json")
                .content(json.writeValueAsString(json.createObjectNode().put("name",name).put("color","#2563eb"))));
    }
    long tag(String actor) throws Exception { return data(createTag(unique(),actor)).path("id").asLong(); }
    ResultActions move(long id,Long parent,String actor) throws Exception {
        ObjectNode body=json.createObjectNode(); if(parent==null) body.putNull("parentId"); else body.put("parentId",parent);
        return mvc.perform(post("/api/v1/categories/"+id+"/move").header("Authorization",actor)
                .contentType("application/json").content(json.writeValueAsBytes(body)));
    }
    JsonNode read(String path,String actor) throws Exception {
        return data(mvc.perform(get("/api/v1"+path).header("Authorization",actor)));
    }
    JsonNode named(JsonNode values,long id) {
        for(JsonNode item:values) if(item.path("id").asLong()==id) return item;
        fail("Expected resource id="+id); return null;
    }
    Set<Long> ids(JsonNode values) {
        Set<Long> result=new HashSet<>(); for(JsonNode item:values) result.add(item.path("id").asLong()); return result;
    }
    byte[] pdf() throws IOException {
        try(PDDocument doc=new PDDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            doc.addPage(new PDPage()); doc.getDocumentInformation().setTitle(UUID.randomUUID().toString());
            doc.save(out); return out.toByteArray();
        }
    }
    ResultActions upload(long category,List<Long> tags,String visibility,String actor,byte[] file) throws Exception {
        ObjectNode body=json.createObjectNode().put("title",unique()).put("abstract","knowledge test abstract")
                .put("year",2026).put("categoryId",category).put("visibility",visibility)
                .put("copyrightAgreed",visibility.equals("shared"));
        body.putArray("authors").add("Knowledge author"); body.putArray("keywords").add("knowledge");
        com.fasterxml.jackson.databind.node.ArrayNode tagIds=body.putArray("tagIds");
        tags.forEach(tagIds::add);
        return mvc.perform(multipart("/api/v1/papers")
                .file(new MockMultipartFile("payload","payload.json","application/json",json.writeValueAsBytes(body)))
                .file(new MockMultipartFile("file","test.pdf","application/pdf",file)).header("Authorization",actor));
    }
    long paper(long category,List<Long> tags,String state,String actor) throws Exception {
        String visibility="private".equals(state)?"private":"shared";
        long id=data(upload(category,tags,visibility,actor,pdf())).path("id").asLong();
        if(!"private".equals(state)) {
            if("offline".equals(state))
                jdbc.update("UPDATE paper SET review_status='offline',offline_reason='withdrawn' WHERE id=?",id);
            else jdbc.update("UPDATE paper SET review_status=? WHERE id=?",state,id);
        }
        return id;
    }
    long value(String sql,Object... args) { return jdbc.queryForObject(sql,Long.class,args); }
    long files() throws IOException {
        try(Stream<Path> paths=Files.list(storage.root().resolve("papers"))) { return paths.count(); }
    }
    long logCount(String prefix,long id) {
        return value("SELECT COUNT(*) FROM operation_log WHERE action='敏感数据访问' AND target LIKE ?",prefix+"Id="+id+";%");
    }
    void assertAcyclic(long start) {
        Set<Long> seen=new HashSet<>(); Long current=start;
        while(current!=null) {
            assertTrue(seen.add(current),"category cycle");
            current=jdbc.queryForObject("SELECT parent_id FROM category WHERE id=?",Long.class,current);
        }
        assertTrue(seen.size()<=10);
    }

    @Test void categoryAndTagOwnershipAreDerivedFromTheDatabasePrincipal() throws Exception {
        String name=unique();
        JsonNode personal=data(createCategory(name,null,user));
        JsonNode platform=data(createCategory(name,null,admin));
        JsonNode foreign=data(createCategory(name,null,other));
        assertTrue(personal.path("custom").asBoolean()); assertEquals(1,personal.path("ownerId").asLong());
        assertFalse(platform.path("custom").asBoolean()); assertTrue(platform.path("ownerId").isNull());
        assertEquals(2,foreign.path("ownerId").asLong());
        JsonNode ownTag=data(createTag(name,user)), platformTag=data(createTag(name,admin)), otherTag=data(createTag(name,other));
        assertEquals("#2563EB",ownTag.path("color").asText());
        assertTrue(ownTag.path("custom").asBoolean()); assertFalse(platformTag.path("custom").asBoolean());
        JsonNode list=read("/categories?userId=2",user);
        assertTrue(ids(list).contains(personal.path("id").asLong()));
        assertFalse(ids(list).contains(foreign.path("id").asLong()));
        assertFalse(ids(read("/tags?userId=2",user)).contains(otherTag.path("id").asLong()));
        assertTrue(ids(read("/categories",admin)).contains(foreign.path("id").asLong()));
        assertTrue(logCount("category",foreign.path("id").asLong())>0);
    }
    @Test void categoryDescriptionAndResponseFieldsMatchTheFrontend() throws Exception {
        JsonNode platform=named(read("/categories",user),1);
        assertEquals("AI 及其子领域",platform.path("desc").asText());
        assertFalse(platform.has("description")); assertTrue(platform.has("paperCount"));
        for(String hidden:Arrays.asList("ownerScope","parentScope","createdAt")) assertFalse(platform.has(hidden));
        JsonNode custom=data(createCategory(unique(),null,user));
        assertTrue(custom.path("parentId").isNull()); assertEquals("",custom.path("desc").asText());
        assertEquals(0,custom.path("paperCount").asLong());
    }
    @Test void normalUsersCannotModifyPlatformOrAnotherUsersDictionaries() throws Exception {
        long platform=category(null,admin), foreign=category(null,other), platformTag=tag(admin), foreignTag=tag(other);
        mvc.perform(patch("/api/v1/categories/"+platform).header("Authorization",user).contentType("application/json").content("{\"name\":\"x\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(1002));
        mvc.perform(delete("/api/v1/categories/"+platform).header("Authorization",user)).andExpect(status().isForbidden());
        move(platform,null,user).andExpect(status().isForbidden());
        for(long id:new long[]{foreign}) {
            mvc.perform(patch("/api/v1/categories/"+id).header("Authorization",user).contentType("application/json").content("{\"name\":\"x\"}"))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/categories/"+id+"/papers").header("Authorization",user)).andExpect(status().isNotFound());
            mvc.perform(delete("/api/v1/categories/"+id).header("Authorization",user)).andExpect(status().isNotFound());
        }
        for(long id:new long[]{platformTag,foreignTag}) {
            int expected=id==platformTag?403:404;
            assertEquals(expected,mvc.perform(patch("/api/v1/tags/"+id).header("Authorization",user).contentType("application/json")
                    .content("{\"name\":\"x\",\"color\":\"#123456\"}")).andReturn().getResponse().getStatus());
            assertEquals(expected,mvc.perform(delete("/api/v1/tags/"+id).header("Authorization",user)).andReturn().getResponse().getStatus());
        }
        mvc.perform(get("/api/v1/tags/"+foreignTag+"/papers").header("Authorization",user)).andExpect(status().isNotFound());
    }
    @Test void adminCanEditAnotherUsersItemsWithoutChangingTheirOwnership() throws Exception {
        long category=category(null,other),tag=tag(other);
        mvc.perform(patch("/api/v1/categories/"+category).header("Authorization",admin).contentType("application/json")
                .content("{\"name\":\""+unique()+"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        mvc.perform(patch("/api/v1/tags/"+tag).header("Authorization",admin).contentType("application/json")
                .content("{\"name\":\""+unique()+"\",\"color\":\"#aabbcc\"}")).andExpect(status().isOk());
        assertEquals(2,value("SELECT owner_id FROM category WHERE id=?",category));
        assertEquals(2,value("SELECT owner_id FROM tag WHERE id=?",tag));
        assertTrue(logCount("category",category)>0); assertTrue(logCount("tag",tag)>0);
    }
    @Test void namesAreUniquePerOwnerAndSiblingAndMovesAlsoHonorTheUniqueConstraint() throws Exception {
        String name=unique();
        long root=data(createCategory(name,null,user)).path("id").asLong();
        createCategory(name.toUpperCase(Locale.ROOT),null,user).andExpect(status().isConflict());
        createCategory(name,null,other).andExpect(status().isOk());
        createCategory(name,null,admin).andExpect(status().isOk());
        long parent=category(null,user),child=data(createCategory(name,parent,user)).path("id").asLong();
        move(child,null,user).andExpect(status().isConflict());
        assertEquals(parent,value("SELECT parent_id FROM category WHERE id=?",child));
        long another=category(null,user);
        mvc.perform(patch("/api/v1/categories/"+another).header("Authorization",user).contentType("application/json")
                .content(json.writeValueAsString(json.createObjectNode().put("name",name)))).andExpect(status().isConflict());
        createTag(name,user).andExpect(status().isOk());
        createTag(name.toUpperCase(Locale.ROOT),user).andExpect(status().isConflict());
        createTag(name,other).andExpect(status().isOk());
        createTag(name,admin).andExpect(status().isOk());
        long anotherTag=tag(user);
        mvc.perform(patch("/api/v1/tags/"+anotherTag).header("Authorization",user).contentType("application/json")
                .content(json.writeValueAsString(json.createObjectNode().put("name",name).put("color","#123456"))))
                .andExpect(status().isConflict());
        assertEquals("#2563EB",jdbc.queryForObject("SELECT color FROM tag WHERE id=?",String.class,anotherTag));
        assertAcyclic(root);
    }
    @Test void concurrentDuplicateCreationReturnsOneSuccessAndOneConflict() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            String categoryName=unique(),tagName=unique();
            for(boolean tags:new boolean[]{false,true}) {
                CountDownLatch start=new CountDownLatch(1);
                Callable<Integer> call=()->{
                    assertTrue(start.await(5,TimeUnit.SECONDS));
                    return (tags?createTag(tagName,user):createCategory(categoryName,null,user)).andReturn().getResponse().getStatus();
                };
                Future<Integer> one=pool.submit(call),two=pool.submit(call); start.countDown();
                List<Integer> codes=Arrays.asList(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS));
                Collections.sort(codes); assertEquals(Arrays.asList(200,409),codes);
            }
        } finally { pool.shutdownNow(); }
    }
    @Test void categoryParentsRespectTheItemsOwnerEvenForAdminMoves() throws Exception {
        long platform=category(null,admin),own=category(null,user),otherRoot=category(null,other);
        long child=category(own,user);
        move(child,platform,user).andExpect(status().isOk());
        move(child,otherRoot,user).andExpect(status().isNotFound());
        move(child,otherRoot,admin).andExpect(status().isBadRequest());
        move(platform,own,admin).andExpect(status().isBadRequest());
        createCategory(unique(),own,admin).andExpect(status().isBadRequest());
        createCategory(unique(),otherRoot,user).andExpect(status().isNotFound());
        move(child,own,user).andExpect(status().isOk());
        move(child,null,user).andExpect(status().isOk());
        assertNull(jdbc.queryForObject("SELECT parent_id FROM category WHERE id=?",Long.class,child));
    }
    @Test void cyclesAndDepthBeyondTenAreRejectedIncludingTheWholeMovedSubtree() throws Exception {
        List<Long> chain=new ArrayList<>(); Long parent=null;
        for(int i=0;i<10;i++) { parent=category(parent,user); chain.add(parent); }
        createCategory(unique(),parent,user).andExpect(status().isBadRequest());
        move(chain.get(0),chain.get(9),user).andExpect(status().isBadRequest());
        move(chain.get(0),chain.get(0),user).andExpect(status().isBadRequest());
        long branch=category(null,user),leaf=category(branch,user);
        move(branch,chain.get(8),user).andExpect(status().isBadRequest());
        move(branch,chain.get(7),user).andExpect(status().isOk());
        assertAcyclic(leaf);
        assertEquals(10,countDepth(leaf));
    }
    int countDepth(long id) {
        int count=0; Long current=id;
        while(current!=null) { count++; current=jdbc.queryForObject("SELECT parent_id FROM category WHERE id=?",Long.class,current); }
        return count;
    }
    @Test void simultaneousMutualMovesCannotCreateACycle() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            for(int attempt=0;attempt<4;attempt++) {
                long one=category(null,user),two=category(null,user);
                CountDownLatch start=new CountDownLatch(1);
                Future<Integer> a=pool.submit(()->{ start.await(); return move(one,two,user).andReturn().getResponse().getStatus(); });
                Future<Integer> b=pool.submit(()->{ start.await(); return move(two,one,user).andReturn().getResponse().getStatus(); });
                start.countDown();
                int first=a.get(10,TimeUnit.SECONDS),second=b.get(10,TimeUnit.SECONDS);
                assertTrue((first==200&&(second==400||second==409))||(second==200&&(first==400||first==409)));
                assertAcyclic(one); assertAcyclic(two);
            }
        } finally { pool.shutdownNow(); }
    }
    @Test void existingCorruptCyclesFailFastOnRead() throws Exception {
        long one=category(null,user),two=category(one,user);
        jdbc.update("UPDATE category SET parent_id=? WHERE id=?",two,one);
        try {
            mvc.perform(get("/api/v1/categories").header("Authorization",user))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(1004));
        } finally { jdbc.update("UPDATE category SET parent_id=NULL WHERE id=?",one); }
    }
    @Test void countsAndListsOnlyIncludeVisiblePapersAndCategoriesIncludeDescendants() throws Exception {
        long root=category(null,admin),child=category(root,admin),tag=tag(admin);
        long ownPrivate=paper(child,Arrays.asList(tag),"private",user);
        long foreignPrivate=paper(child,Arrays.asList(tag),"private",other);
        long approved=paper(child,Arrays.asList(tag),"approved",other);
        long pending=paper(root,Arrays.asList(tag),"pending",other);
        long ownRejected=paper(root,Arrays.asList(tag),"rejected",user);
        long ownOffline=paper(child,Arrays.asList(tag),"offline",user);
        long foreignOffline=paper(child,Arrays.asList(tag),"offline",other);
        long removed=paper(child,Arrays.asList(tag),"approved",other);
        jdbc.update("UPDATE paper SET deleted_at=NOW() WHERE id=?",removed);
        Set<Long> expected=new HashSet<>(Arrays.asList(ownPrivate,approved,ownRejected,ownOffline));
        assertEquals(expected,ids(read("/categories/"+root+"/papers",user)));
        assertEquals(expected,ids(read("/tags/"+tag+"/papers",user)));
        assertEquals(4,named(read("/categories",user),root).path("paperCount").asLong());
        assertEquals(3,named(read("/categories",user),child).path("paperCount").asLong());
        assertEquals(4,named(read("/tags",user),tag).path("paperCount").asLong());
        Set<Long> adminExpected=new HashSet<>(Arrays.asList(ownPrivate,foreignPrivate,approved,pending,ownRejected,ownOffline,foreignOffline));
        assertEquals(adminExpected,ids(read("/categories/"+root+"/papers",admin)));
        assertEquals(7,named(read("/categories",admin),root).path("paperCount").asLong());
        assertEquals(7,named(read("/tags",admin),tag).path("paperCount").asLong());
        assertTrue(logCount("paper",foreignPrivate)>0);
    }
    @Test void categoryListsReturnTheSameSafePaperShapeAndHideReviewComments() throws Exception {
        long category=category(null,admin),tag=tag(admin);
        long id=paper(category,Arrays.asList(tag),"approved",other);
        jdbc.update("UPDATE paper SET review_comment='internal reason' WHERE id=?",id);
        JsonNode item=named(read("/categories/"+category+"/papers",user),id);
        assertEquals("陈思羽",item.path("uploaderName").asText());
        assertEquals(1,item.path("tagIds").size()); assertEquals(tag,item.path("tagIds").get(0).asLong());
        assertTrue(item.has("abstract")); assertTrue(item.has("fileSizeMB")); assertFalse(item.has("reviewComment"));
        assertFalse(item.has("filePath")); assertFalse(item.has("fileSha256")); assertFalse(item.has("version"));
        assertFalse(named(read("/tags/"+tag+"/papers",user),id).has("reviewComment"));
    }
    @Test void platformSubtreeWithPrivateChildrenCannotBeDeletedUntilTheyAreMoved() throws Exception {
        long platform=category(null,admin),child=category(platform,admin),personal=category(child,other);
        mvc.perform(delete("/api/v1/categories/"+platform).header("Authorization",admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(1004));
        assertEquals(3,value("SELECT COUNT(*) FROM category WHERE id IN (?,?,?)",platform,child,personal));
        move(personal,null,admin).andExpect(status().isOk());
        JsonNode names=data(mvc.perform(delete("/api/v1/categories/"+platform).header("Authorization",admin)));
        assertEquals(2,names.size());
        assertEquals(1,value("SELECT COUNT(*) FROM category WHERE id=?",personal));
    }
    @Test void categoryDeletionUnlinksPapersButPreservesFilesApprovalAndPersonalHistory() throws Exception {
        long root=category(null,admin),child=category(root,admin);
        long id=paper(child,Arrays.asList(1L),"approved",user);
        long removed=paper(child,Collections.emptyList(),"private",user);
        jdbc.update("UPDATE paper SET deleted_at=NOW() WHERE id=?",removed);
        jdbc.update("INSERT INTO favorite(user_id,paper_id) VALUES(2,?)",id);
        jdbc.update("INSERT INTO reading_record(user_id,paper_id) VALUES(2,?)",id);
        jdbc.update("INSERT INTO note(user_id,paper_id,paper_title,title,content) VALUES(2,?,'snapshot','note','retained note')",id);
        jdbc.update("INSERT INTO review_record(paper_id,reviewer_id,paper_title,uploader_id,action) VALUES(?,101,'snapshot',1,'approved')",id);
        String key=jdbc.queryForObject("SELECT file_path FROM paper WHERE id=?",String.class,id);
        long version=value("SELECT version FROM paper WHERE id=?",id);
        String rootName=jdbc.queryForObject("SELECT name FROM category WHERE id=?",String.class,root);
        String childName=jdbc.queryForObject("SELECT name FROM category WHERE id=?",String.class,child);
        JsonNode names=data(mvc.perform(delete("/api/v1/categories/"+root).header("Authorization",admin)));
        assertEquals(Arrays.asList(rootName,childName),Arrays.asList(names.get(0).asText(),names.get(1).asText()));
        assertNull(jdbc.queryForObject("SELECT category_id FROM paper WHERE id=?",Long.class,id));
        assertEquals(version+1,value("SELECT version FROM paper WHERE id=?",id));
        assertNull(jdbc.queryForObject("SELECT category_id FROM paper WHERE id=?",Long.class,removed));
        assertEquals(1,value("SELECT version FROM paper WHERE id=?",removed));
        assertEquals("approved",jdbc.queryForObject("SELECT review_status FROM paper WHERE id=?",String.class,id));
        assertTrue(Files.exists(storage.resolve(key)));
        for(String table:Arrays.asList("favorite","reading_record","note","review_record"))
            assertEquals(1,value("SELECT COUNT(*) FROM "+table+" WHERE paper_id=?",id));
        mvc.perform(get("/api/v1/papers/"+id+"/file").header("Authorization",other)).andExpect(status().isOk());
    }
    @Test void tagDeletionOnlyRemovesThatAssociationAndBumpsVersionAlsoForDeletedPapers() throws Exception {
        long category=category(null,admin),target=tag(admin),keep=tag(admin);
        long id=paper(category,Arrays.asList(target,keep),"private",user);
        long removed=paper(category,Arrays.asList(target),"private",user);
        jdbc.update("UPDATE paper SET deleted_at=NOW() WHERE id=?",removed);
        data(mvc.perform(delete("/api/v1/tags/"+target).header("Authorization",admin)));
        assertEquals(0,value("SELECT COUNT(*) FROM paper_tag WHERE tag_id=?",target));
        assertEquals(1,value("SELECT COUNT(*) FROM paper_tag WHERE paper_id=? AND tag_id=?",id,keep));
        assertEquals(1,value("SELECT version FROM paper WHERE id=?",id));
        assertEquals(1,value("SELECT version FROM paper WHERE id=?",removed));
        assertEquals(category,value("SELECT category_id FROM paper WHERE id=?",id));
        assertEquals("private",jdbc.queryForObject("SELECT visibility FROM paper WHERE id=?",String.class,id));
        mvc.perform(get("/api/v1/papers/"+id+"/file").header("Authorization",user)).andExpect(status().isOk());
    }
    @Test void categoryDeletionRollsBackTreePaperLinksAndVersionIfItsLogFails() throws Exception {
        long root=category(null,admin),child=category(root,admin);
        long id=paper(child,Collections.emptyList(),"private",user);
        doThrow(new DataAccessResourceFailureException("simulated log failure")).when(audit)
                .append(eq(101L),eq("分类删除"),anyString(),anyString(),any());
        mvc.perform(delete("/api/v1/categories/"+root).header("Authorization",admin)).andExpect(status().isInternalServerError());
        assertEquals(2,value("SELECT COUNT(*) FROM category WHERE id IN (?,?)",root,child));
        assertEquals(child,value("SELECT category_id FROM paper WHERE id=?",id));
        assertEquals(0,value("SELECT version FROM paper WHERE id=?",id));
    }
    @Test void tagDeletionRollsBackAssociationsAndVersionIfItsLogFails() throws Exception {
        long target=tag(admin),id=paper(1,Arrays.asList(target),"private",user);
        doThrow(new DataAccessResourceFailureException("simulated log failure")).when(audit)
                .append(eq(101L),eq("标签删除"),anyString(),anyString(),any());
        mvc.perform(delete("/api/v1/tags/"+target).header("Authorization",admin)).andExpect(status().isInternalServerError());
        assertEquals(1,value("SELECT COUNT(*) FROM tag WHERE id=?",target));
        assertEquals(1,value("SELECT COUNT(*) FROM paper_tag WHERE paper_id=? AND tag_id=?",id,target));
        assertEquals(0,value("SELECT version FROM paper WHERE id=?",id));
    }
    @Test void adminPrivateCountsAndPaperListsFailClosedIfAuditIsUnavailable() throws Exception {
        long category=category(null,admin),target=tag(admin);
        long id=paper(category,Arrays.asList(target),"private",user);
        doThrow(new DataAccessResourceFailureException("simulated private paper audit outage")).when(audit)
                .append(eq(101L),eq("敏感数据访问"),startsWith("paperId="),anyString(),any());
        for(String path:Arrays.asList("/categories","/tags","/categories/"+category+"/papers","/tags/"+target+"/papers"))
            mvc.perform(get("/api/v1"+path).header("Authorization",admin))
                    .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(1500))
                    .andExpect(jsonPath("$.data").isEmpty());
        assertTrue(ids(read("/categories/"+category+"/papers",user)).contains(id));
    }
    @Test void adminPrivateDictionaryMetadataAlsoRequiresSuccessfulAudit() throws Exception {
        long category=category(null,other),target=tag(other);
        doThrow(new DataAccessResourceFailureException("simulated category audit outage")).when(audit)
                .append(eq(101L),eq("敏感数据访问"),startsWith("categoryId="),anyString(),any());
        mvc.perform(get("/api/v1/categories").header("Authorization",admin)).andExpect(status().isInternalServerError());
        mvc.perform(get("/api/v1/categories/"+category+"/papers").header("Authorization",admin)).andExpect(status().isInternalServerError());
        reset(audit);
        doThrow(new DataAccessResourceFailureException("simulated tag audit outage")).when(audit)
                .append(eq(101L),eq("敏感数据访问"),startsWith("tagId="),anyString(),any());
        mvc.perform(get("/api/v1/tags").header("Authorization",admin)).andExpect(status().isInternalServerError());
        mvc.perform(get("/api/v1/tags/"+target+"/papers").header("Authorization",admin)).andExpect(status().isInternalServerError());
    }
    @Test void privateReadAuditSurvivesAnOuterTransactionRollback() throws Exception {
        long id=category(null,other);
        long before=logCount("category",id);
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status->{
            categories.list(new SessionPrincipal(101,"admin","admin"));
            throw new IllegalStateException("rollback outer");
        }));
        assertEquals(before+1,logCount("category",id));
    }
    @Test void concurrentCategoryDeleteAndPaperEditUseDictionaryBeforePaperLockOrder() throws Exception {
        long category=category(null,admin),id=paper(category,Collections.emptyList(),"private",user);
        CountDownLatch deleting=new CountDownLatch(1),release=new CountDownLatch(1),editing=new CountDownLatch(1);
        doAnswer(invocation->{
            deleting.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS));
            return jdbc.update("UPDATE paper SET category_id=NULL,version=version+1 WHERE id=?",id);
        }).when(relations).clearCategory(id);
        doAnswer(invocation->{ editing.countDown(); return invocation.callRealMethod(); }).when(locks).beforePaperWrite();
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Integer> deletion=pool.submit(()->mvc.perform(delete("/api/v1/categories/"+category)
                    .header("Authorization",admin)).andReturn().getResponse().getStatus());
            assertTrue(deleting.await(5,TimeUnit.SECONDS));
            Future<Integer> edit=pool.submit(()->mvc.perform(patch("/api/v1/papers/"+id).header("Authorization",user)
                    .contentType("application/json").content("{\"categoryId\":"+category+"}")).andReturn().getResponse().getStatus());
            assertTrue(editing.await(5,TimeUnit.SECONDS)); release.countDown();
            assertEquals(200,deletion.get(10,TimeUnit.SECONDS).intValue());
            assertEquals(422,edit.get(10,TimeUnit.SECONDS).intValue());
        } finally { release.countDown(); pool.shutdownNow(); }
        assertNull(jdbc.queryForObject("SELECT category_id FROM paper WHERE id=?",Long.class,id));
    }
    @Test void concurrentTagDeleteAndPaperEditCannotLeaveAnOrphanAssociation() throws Exception {
        long target=tag(admin),id=paper(1,Arrays.asList(target),"private",user);
        CountDownLatch deleting=new CountDownLatch(1),release=new CountDownLatch(1),editing=new CountDownLatch(1);
        doAnswer(invocation->{
            deleting.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS));
            return jdbc.update("DELETE FROM paper_tag WHERE tag_id=?",target);
        }).when(relations).clearTag(target);
        doAnswer(invocation->{ editing.countDown(); return invocation.callRealMethod(); }).when(locks).beforePaperWrite();
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Integer> deletion=pool.submit(()->mvc.perform(delete("/api/v1/tags/"+target)
                    .header("Authorization",admin)).andReturn().getResponse().getStatus());
            assertTrue(deleting.await(5,TimeUnit.SECONDS));
            Future<Integer> edit=pool.submit(()->mvc.perform(patch("/api/v1/papers/"+id).header("Authorization",user)
                    .contentType("application/json").content("{\"tagIds\":["+target+"]}")).andReturn().getResponse().getStatus());
            assertTrue(editing.await(5,TimeUnit.SECONDS)); release.countDown();
            assertEquals(200,deletion.get(10,TimeUnit.SECONDS).intValue());
            assertEquals(422,edit.get(10,TimeUnit.SECONDS).intValue());
        } finally { release.countDown(); pool.shutdownNow(); }
        assertEquals(0,value("SELECT COUNT(*) FROM paper_tag WHERE tag_id=?",target));
    }
    @Test void uploadRacingCategoryDeletionRollsBackItsNewFile() throws Exception {
        long category=category(null,admin),existing=paper(category,Collections.emptyList(),"private",user);
        long before=files();
        CountDownLatch deleting=new CountDownLatch(1),release=new CountDownLatch(1),uploading=new CountDownLatch(1);
        doAnswer(invocation->{
            deleting.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS));
            return jdbc.update("UPDATE paper SET category_id=NULL,version=version+1 WHERE id=?",existing);
        }).when(relations).clearCategory(existing);
        doAnswer(invocation->{ uploading.countDown(); return invocation.callRealMethod(); }).when(locks).beforePaperWrite();
        byte[] bytes=pdf(); ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Integer> deletion=pool.submit(()->mvc.perform(delete("/api/v1/categories/"+category)
                    .header("Authorization",admin)).andReturn().getResponse().getStatus());
            assertTrue(deleting.await(5,TimeUnit.SECONDS));
            Future<Integer> upload=pool.submit(()->upload(category,Collections.emptyList(),"private",user,bytes)
                    .andReturn().getResponse().getStatus());
            assertTrue(uploading.await(5,TimeUnit.SECONDS)); release.countDown();
            assertEquals(200,deletion.get(10,TimeUnit.SECONDS).intValue());
            assertEquals(422,upload.get(10,TimeUnit.SECONDS).intValue());
        } finally { release.countDown(); pool.shutdownNow(); }
        assertEquals(before,files());
    }
    @Test void validationRejectsOwnershipSpoofingMissingMoveTargetAndUnsafeValues() throws Exception {
        for(String body:Arrays.asList("{\"name\":\"x\",\"ownerId\":2}","{\"name\":\"x\",\"custom\":false}","{\"name\":\" \"}",
                "{\"name\":123}","{\"name\":\"x\",\"parentId\":1.5}","{\"name\":\"x\",\"parentId\":-1}"))
            mvc.perform(post("/api/v1/categories").header("Authorization",user).contentType("application/json").content(body))
                    .andExpect(status().isUnprocessableEntity());
        long id=category(null,user);
        mvc.perform(post("/api/v1/categories/"+id+"/move").header("Authorization",user)
                .contentType("application/json").content("{}")).andExpect(status().isUnprocessableEntity());
        for(String color:Arrays.asList("red","#abc","javascript:alert(1)","url(x)","#GGGGGG"))
            mvc.perform(post("/api/v1/tags").header("Authorization",user).contentType("application/json")
                    .content(json.writeValueAsString(json.createObjectNode().put("name",unique()).put("color",color))))
                    .andExpect(status().isUnprocessableEntity());
        for(String path:Arrays.asList("/categories/0/papers","/tags/9007199254740992/papers"))
            mvc.perform(get("/api/v1"+path).header("Authorization",user)).andExpect(status().isUnprocessableEntity());
    }
    @Test void dictionaryEndpointsRequireAuthenticationAndRejectDisabledAccounts() throws Exception {
        mvc.perform(get("/api/v1/categories")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/tags").contentType("application/json").content("{\"name\":\"x\",\"color\":\"#123456\"}"))
                .andExpect(status().isUnauthorized());
        jdbc.update("UPDATE `user` SET status='disabled' WHERE id=1");
        mvc.perform(get("/api/v1/tags").header("Authorization",user)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/categories").header("Authorization",user).contentType("application/json")
                .content("{\"name\":\"x\",\"parentId\":null}")).andExpect(status().isUnauthorized());
    }
}

