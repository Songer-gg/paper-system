package cn.paperhub.profile;

import cn.paperhub.profile.mapper.AccountWriteMapper;
import cn.paperhub.support.SharingSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="paperhub.storage.cleanup-enabled=false")
@AutoConfigureMockMvc
class ProfileAdminIT extends SharingSupport {
    @Autowired PasswordEncoder encoder;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    @SpyBean AccountWriteMapper accountWrites;
    final List<Long> created=new ArrayList<>();
    final String original="Original123!";
    @AfterEach void cleanAccounts() {
        reset(accountWrites);
        for(Long id:created) jdbc.update("UPDATE `user` SET role='user' WHERE id=?",id);
        created.clear();
    }
    long account(String role) {
        String key="account"+unique().replace("-","");
        jdbc.update("INSERT INTO `user`(username,password,name,email,role,status) VALUES(?,?,?,?,?,'active')",
                key,encoder.encode(original),"Account "+key,key+"@example.com",role);
        long id=value("SELECT id FROM `user` WHERE username=?",key);created.add(id);return id;
    }
    String token(long id) { return "Bearer "+jwt.issue(users.selectById(id)); }
    String username(long id) { return jdbc.queryForObject("SELECT username FROM `user` WHERE id=?",String.class,id); }
    ResultActions login(long id,String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsBytes(body("username",username(id),"password",password,"role",users.selectById(id).getRole())))
                .with(request->{request.setRemoteAddr("test-"+unique());return request;}));
    }
    ResultActions change(String actor,String old,String next,String confirm) throws Exception {
        return api("PUT","/me/password",body("oldPassword",old,"newPassword",next,"confirmPassword",confirm),actor);
    }
    ResultActions setStatus(long id,String desired,String actor) throws Exception {
        return api("PATCH","/admin/users/"+id+"/status",body("status",desired),actor);
    }
    long version(long id) { return value("SELECT token_version FROM `user` WHERE id=?",id); }
    @Test void profileIsOwnSafeAndCountsAllUndeletedUploads() throws Exception {
        long id=account("user");String actor=token(id);
        paper("private",actor);long shared=paper("shared",actor);long deleted=paper("private",actor);
        ok(api("DELETE","/papers/"+deleted,null,actor));
        JsonNode profile=read("/me/profile?userId=1",actor);
        assertEquals(id,profile.path("id").asLong());assertEquals(2,profile.path("uploadedCount").asInt());
        assertEquals(9,profile.size());assertEquals("",profile.path("avatar").asText());
        assertFalse(profile.has("password"));assertFalse(profile.has("tokenVersion"));
        assertEquals("shared",read("/papers/"+shared,actor).path("visibility").asText());
    }
    @Test void partialProfileUpdatesPreserveOtherFieldsAndToken() throws Exception {
        long id=account("user");String actor=token(id);String email=users.selectById(id).getEmail();
        JsonNode renamed=ok(api("PATCH","/me/profile",body("name","  Changed name  "),actor));
        assertEquals("Changed name",renamed.path("name").asText());assertEquals(email,renamed.path("email").asText());
        String next="Updated"+unique()+"@Example.COM";
        JsonNode updated=ok(api("PATCH","/me/profile",body("email",next),actor));
        assertEquals(next.toLowerCase(Locale.ROOT),updated.path("email").asText());assertEquals("Changed name",updated.path("name").asText());
        assertEquals(0,version(id));read("/me/profile",actor);
        ok(api("POST","/auth/login",body("username",updated.path("email").asText(),"password",original,"role","user"),null));
    }
    @Test void profilesRejectSpoofingNullEmptyAndBadEmail() throws Exception {
        for(JsonNode input:new JsonNode[]{body(),body("name",null),body("email",null),body("name"," "),body("email","bad"),
                body("role","admin"),body("userId",2),body("avatar","data:image/png;base64,a"),body("password","bad"),body("name",12)})
            api("PATCH","/me/profile",input,user).andExpect(status().isUnprocessableEntity());
    }
    @Test void emailUniquenessUsesDatabaseCollationAndWholePatchIsAtomic() throws Exception {
        long first=account("user"),second=account("user");String actor=token(first);
        String email=users.selectById(second).getEmail().toUpperCase(Locale.ROOT),name=users.selectById(first).getName();
        api("PATCH","/me/profile",body("name","Must roll back","email",email),actor).andExpect(status().isConflict());
        assertEquals(name,users.selectById(first).getName());
    }
    @Test void concurrentEmailsProduceOneWinner() throws Exception {
        long a=account("user"),b=account("user");String mail="race"+unique()+"@example.com";
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Future<Integer> one=pool.submit(()->{start.await();return api("PATCH","/me/profile",body("email",mail),token(a)).andReturn().getResponse().getStatus();});
            Future<Integer> two=pool.submit(()->{start.await();return api("PATCH","/me/profile",body("email",mail),token(b)).andReturn().getResponse().getStatus();});
            start.countDown();Set<Integer> results=new HashSet<>(Arrays.asList(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS)));
            assertEquals(new HashSet<>(Arrays.asList(200,409)),results);assertEquals(1,value("SELECT COUNT(*) FROM `user` WHERE email=?",mail));
        } finally {pool.shutdownNow();}
    }
    @Test void profileLogFailureRollsBackAllChanges() throws Exception {
        long id=account("user");String name=users.selectById(id).getName();
        failLog("资料修改");api("PATCH","/me/profile",body("name","Rollback"),token(id)).andExpect(status().isInternalServerError());
        assertEquals(name,users.selectById(id).getName());
    }
    void failLog(String action) {
        doThrow(new DataAccessResourceFailureException("simulated account logging failure"))
                .when(audit).append(anyLong(),eq(action),anyString(),anyString(),any());
    }
    @Test void passwordChangeInvalidatesEveryOldTokenAndKeepsResetTimestamp() throws Exception {
        long id=account("user");String first=token(id),second=token(id),next="New Secure 123!";
        jdbc.update("UPDATE `user` SET password_reset_at='2025-01-01 12:00:00' WHERE id=?",id);
        JsonNode result=ok(change(first,original,next,next));assertTrue(result.path("ok").asBoolean());assertEquals(2,result.size());
        assertEquals(1,version(id));assertTrue(encoder.matches(next,users.selectById(id).getPassword()));
        for(String old:new String[]{first,second}) api("GET","/me/profile",null,old).andExpect(status().isUnauthorized());
        login(id,original).andExpect(status().isUnprocessableEntity());ok(login(id,next));
        assertEquals(LocalDateTime.of(2025,1,1,12,0),users.selectById(id).getPasswordResetAt());
    }
    @Test void badPasswordInputsHaveNoSideEffects() throws Exception {
        long id=account("user");String actor=token(id);
        change(actor,"wrong",original+"new",original+"new").andExpect(status().isBadRequest());
        change(actor,original,original,original).andExpect(status().isBadRequest());
        change(actor,original,"12345678","87654321").andExpect(status().isBadRequest());
        change(actor,original,"1234567","1234567").andExpect(status().isUnprocessableEntity());
        String longUtf8=String.join("",Collections.nCopies(25,"密"));
        change(actor,original,longUtf8,longUtf8).andExpect(status().isUnprocessableEntity());
        api("PUT","/me/password",body("oldPassword",original,"newPassword","12345678","confirmPassword","12345678","userId",1),actor).andExpect(status().isUnprocessableEntity());
        assertEquals(0,version(id));assertTrue(encoder.matches(original,users.selectById(id).getPassword()));
    }
    @Test void utf8BoundaryPasswordWorksWithoutTrimming() throws Exception {
        long id=account("user");String next=String.join("",Collections.nCopies(24,"密"));
        ok(change(token(id),original,next,next));ok(login(id,next));
        String spaced="  password with spaces  ";ok(change(token(id),next,spaced,spaced));
        ok(login(id,spaced));login(id,spaced.trim()).andExpect(status().isUnprocessableEntity());
    }
    @Test void concurrentPasswordChangesDoNotBothAcceptOldHash() throws Exception {
        long id=account("user");String actor=token(id);CountDownLatch start=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> call=()->{start.await();return change(actor,original,"Changed123!","Changed123!").andReturn().getResponse().getStatus();};
            Future<Integer> a=pool.submit(call),b=pool.submit(call);start.countDown();
            List<Integer> codes=Arrays.asList(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            assertEquals(1,Collections.frequency(codes,200));assertTrue(codes.contains(400)||codes.contains(401));assertEquals(1,version(id));
        } finally {pool.shutdownNow();}
    }
    @Test void passwordLogFailurePreservesPasswordAndSession() throws Exception {
        long id=account("user");String actor=token(id);failLog("密码修改");
        change(actor,original,"Changed123!","Changed123!").andExpect(status().isInternalServerError());
        assertEquals(0,version(id));assertTrue(encoder.matches(original,users.selectById(id).getPassword()));read("/me/profile",actor);
    }
    @Test void adminUserViewsAreSafeAndCountsExcludeDeletedAndPrivate() throws Exception {
        long id=account("user");String actor=token(id);
        paper("private",actor);long pending=paper("shared",actor);long approved=publish(base("shared"),actor);
        long deleted=paper("shared",actor);ok(api("DELETE","/papers/"+deleted,null,actor));
        JsonNode result=read("/admin/users/"+id,admin);
        assertEquals(10,result.size());assertEquals(2,result.path("sharedCount").asInt());assertEquals(1,result.path("contributionCount").asInt());
        assertTrue(result.path("passwordResetAt").isNull());assertFalse(result.has("password"));assertFalse(result.has("avatar"));
        assertEquals("pending",state(pending));assertEquals("approved",state(approved));
        assertEquals(result,named(read("/admin/users",admin),id));
    }
    JsonNode queryUsers(String... params) throws Exception {
        MockHttpServletRequestBuilder request=get("/api/v1/admin/users").header("Authorization",admin);
        for(int i=0;i<params.length;i+=2) request.param(params[i],params[i+1]);return ok(mvc.perform(request));
    }
    @Test void userQueriesFilterLiteralKeywordStatusAndInclusiveDates() throws Exception {
        long id=account("user");String marker=unique();
        jdbc.update("UPDATE `user` SET name=?,status='disabled',registered_at='2025-02-28 23:59:59' WHERE id=?","Literal %_! "+marker,id);
        assertTrue(ids(queryUsers("keyword","%_!","status","disabled","dateFrom","2025-02-28","dateTo","2025-02-28")).contains(id));
        assertFalse(ids(queryUsers("keyword",marker,"status","active")).contains(id));
        assertFalse(ids(queryUsers("keyword",marker,"dateTo","2025-02-27")).contains(id));
        assertTrue(ids(queryUsers("keyword",username(id).toUpperCase(Locale.ROOT))).contains(id));
    }
    @Test void malformedUserFiltersAndMissingUsersAreRejected() throws Exception {
        for(String path:new String[]{"?status=bogus","?dateFrom=2025-02-30","?dateFrom=2025-2-1","?dateFrom=2025-03-01&dateTo=2025-02-01"})
            api("GET","/admin/users"+path,null,admin).andExpect(status().isUnprocessableEntity());
        for(String suffix:new String[]{"","/papers","/favorites","/reading-records","/notes"})
            api("GET","/admin/users/9007199254740991"+suffix,null,admin).andExpect(status().isNotFound());
        setStatus(9007199254740991L,"active",admin).andExpect(status().isNotFound());
        api("POST","/admin/users/9007199254740991/password-reset",null,admin).andExpect(status().isNotFound());
    }
    @Test void disablingAndRestoringNeverRevivesOldToken() throws Exception {
        long id=account("user");String old=token(id);
        ok(setStatus(id,"disabled",admin));assertEquals(1,version(id));
        api("GET","/me/profile",null,old).andExpect(status().isUnauthorized());login(id,original).andExpect(status().isUnprocessableEntity());
        ok(setStatus(id,"disabled",admin));assertEquals(1,version(id));
        ok(setStatus(id,"active",admin));assertEquals(2,version(id));
        api("GET","/me/profile",null,old).andExpect(status().isUnauthorized());ok(login(id,original));
    }
    @Test void statusValidationAndSelfProtectionDoNotChangeVersion() throws Exception {
        long version=version(101);setStatus(101,"disabled",admin).andExpect(status().isBadRequest());assertEquals(version,version(101));
        for(JsonNode input:new JsonNode[]{body(),body("status",null),body("status","bogus"),body("status","active","role","admin"),body("status",1)})
            api("PATCH","/admin/users/1/status",input,admin).andExpect(status().isUnprocessableEntity());
    }
    @Test void crossDisablingAdministratorsLeavesOneActiveAndRejectsRevokedActor() throws Exception {
        long id=account("admin");String second=token(id);CountDownLatch locked=new CountDownLatch(1),competing=new CountDownLatch(1),release=new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation->{
            int sequence=calls.incrementAndGet();if(sequence==2)competing.countDown();
            Object result=sqlSession.selectList(AccountWriteMapper.class.getName()+".lockAdministrators");
            if(sequence==1){locked.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));}return result;
        }).when(accountWrites).lockAdministrators();
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Integer> one=pool.submit(()->setStatus(id,"disabled",admin).andReturn().getResponse().getStatus());
            assertTrue(locked.await(5,TimeUnit.SECONDS));
            Future<Integer> two=pool.submit(()->setStatus(101,"disabled",second).andReturn().getResponse().getStatus());
            assertTrue(competing.await(5,TimeUnit.SECONDS));release.countDown();
            assertEquals(200,one.get(10,TimeUnit.SECONDS));assertEquals(401,two.get(10,TimeUnit.SECONDS));
            assertEquals(1,value("SELECT COUNT(*) FROM `user` WHERE role='admin' AND status='active'"));
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void statusLogFailureRollsBackStateAndVersion() throws Exception {
        long id=account("user");String actor=token(id);failLog("用户禁用");
        setStatus(id,"disabled",admin).andExpect(status().isInternalServerError());
        assertEquals("active",users.selectById(id).getStatus());assertEquals(0,version(id));read("/me/profile",actor);
    }
    @Test void passwordResetIsRandomHashedAndInvalidatesAllPreviousPasswords() throws Exception {
        long id=account("user");String old=token(id);
        String first=ok(api("POST","/admin/users/"+id+"/password-reset",null,admin)).asText();
        assertEquals(24,first.length());assertTrue(encoder.matches(first,users.selectById(id).getPassword()));assertEquals(1,version(id));
        assertNotNull(users.selectById(id).getPasswordResetAt());assertFalse(read("/admin/users/"+id,admin).path("passwordResetAt").isNull());
        api("GET","/me/profile",null,old).andExpect(status().isUnauthorized());login(id,original).andExpect(status().isUnprocessableEntity());
        String signed="Bearer "+ok(login(id,first)).path("token").asText();
        String second=ok(api("POST","/admin/users/"+id+"/password-reset",body(),admin)).asText();assertNotEquals(first,second);
        api("GET","/me/profile",null,signed).andExpect(status().isUnauthorized());ok(login(id,second));assertEquals(2,version(id));
        assertEquals(0,value("SELECT COUNT(*) FROM operation_log WHERE target LIKE ? OR target LIKE ?","%"+first+"%","%"+second+"%"));
    }
    @Test void resetLogFailureRollsBackHashTimestampAndVersion() throws Exception {
        long id=account("user");String hash=users.selectById(id).getPassword();failLog("重置密码");
        api("POST","/admin/users/"+id+"/password-reset",null,admin).andExpect(status().isInternalServerError());
        assertEquals(hash,users.selectById(id).getPassword());assertNull(users.selectById(id).getPasswordResetAt());assertEquals(0,version(id));
    }
    @Test void resetDoesNotAcceptClientSuppliedPasswordOrIdentity() throws Exception {
        for(JsonNode input:new JsonNode[]{body("password","fixed123!"),body("userId",2),body("tokenVersion",0)})
            api("POST","/admin/users/1/password-reset",input,admin).andExpect(status().isUnprocessableEntity());
    }
    @Test void administratorsCanReadPrivatePapersAndPersonalRowsWithResourceAudits() throws Exception {
        long owner=account("user");String actor=token(owner);long privateId=paper("private",actor),shared=publish(base("shared"),actor);
        ok(api("POST","/papers/"+shared+"/favorite",body("favorited",true),actor));
        ok(api("POST","/papers/"+privateId+"/reading",null,actor));
        long note=ok(api("POST","/me/notes",body("paperId",privateId,"title","Personal snapshot","content","Private Markdown "+unique()),actor)).path("id").asLong();
        assertTrue(ids(read("/admin/users/"+owner+"/papers",admin)).contains(privateId));
        JsonNode favorite=read("/admin/users/"+owner+"/favorites",admin);assertEquals(1,favorite.size());assertEquals(shared,favorite.get(0).path("paper").path("id").asLong());
        JsonNode reading=read("/admin/users/"+owner+"/reading-records",admin);assertEquals(1,reading.size());assertEquals(5,reading.get(0).size());assertEquals(privateId,reading.get(0).path("paper").path("id").asLong());
        assertTrue(named(read("/admin/users/"+owner+"/notes",admin),note).path("paperAvailable").asBoolean());
        for(String kind:new String[]{"userId="+owner,"paperId="+privateId,"noteId="+note})
            assertTrue(value("SELECT COUNT(*) FROM operation_log WHERE user_id=101 AND action='敏感数据访问' AND target LIKE ?","%"+kind+"%")>0);
        assertEquals(0,value("SELECT COUNT(*) FROM operation_log WHERE target LIKE '%Private Markdown%'"));
        assertFalse(ids(read("/me/notes",admin)).contains(note));
    }
    @Test void deletedSourcesKeepAdminPlaceholdersAndNotesWhileOfflineMetadataRemainsAccessible() throws Exception {
        long owner=account("user");String actor=token(owner);long source=publish(base("shared"),actor);
        String title=read("/papers/"+source,actor).path("title").asText();
        ok(api("POST","/papers/"+source+"/favorite",body("favorited",true),actor));ok(api("POST","/papers/"+source+"/reading",null,actor));
        long note=ok(api("POST","/me/notes",body("paperId",source,"title","Retain","content","Body"),actor)).path("id").asLong();
        ok(flow(source,"unshare",null,actor));
        assertEquals(source,read("/admin/users/"+owner+"/favorites",admin).get(0).path("paper").path("id").asLong());
        ok(api("DELETE","/papers/"+source,null,actor));
        assertTrue(read("/admin/users/"+owner+"/favorites",admin).get(0).path("paper").isNull());
        assertTrue(read("/admin/users/"+owner+"/reading-records",admin).get(0).path("paper").isNull());
        JsonNode row=named(read("/admin/users/"+owner+"/notes",admin),note);assertFalse(row.path("paperAvailable").asBoolean());assertEquals(title,row.path("paperTitle").asText());
        assertTrue(read("/admin/users/"+owner+"/papers",admin).isEmpty());
    }
    @Test void sensitiveScopeAuditFailureBlocksEveryAdminReadEvenEmptyLists() throws Exception {
        long owner=account("user");failLog("敏感数据访问");
        for(String path:new String[]{"/admin/users","/admin/users/"+owner,"/admin/users/"+owner+"/papers",
                "/admin/users/"+owner+"/favorites","/admin/users/"+owner+"/reading-records","/admin/users/"+owner+"/notes"})
            api("GET",path,null,admin).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.data").isEmpty());
    }
    @Test void rowAuditFailureAlsoPreventsBodyAfterSuccessfulScopeAudit() throws Exception {
        long owner=account("user");String actor=token(owner);long source=paper("private",actor);
        ok(api("POST","/me/notes",body("paperId",source,"title","Secret title","content","Secret body"),actor));
        doThrow(new DataAccessResourceFailureException("simulated record audit failure")).when(audit)
                .append(eq(101L),eq("敏感数据访问"),startsWith("noteId="),anyString(),any());
        api("GET","/admin/users/"+owner+"/notes",null,admin).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.data").isEmpty());
        assertTrue(value("SELECT COUNT(*) FROM operation_log WHERE user_id=101 AND target=?","userId="+owner+";ownerId="+owner+";action=adminUserNotes")>0);
    }
    @Test void normalAndDisabledUsersCannotReachAnyAdminUserRoute() throws Exception {
        for(String path:new String[]{"/admin/users","/admin/users/1","/admin/users/1/papers","/admin/users/1/favorites","/admin/users/1/reading-records","/admin/users/1/notes"}) {
            api("GET",path,null,user).andExpect(status().isForbidden());api("GET",path,null,null).andExpect(status().isUnauthorized());
        }
        setStatus(1,"disabled",user).andExpect(status().isForbidden());api("POST","/admin/users/1/password-reset",null,user).andExpect(status().isForbidden());
        long id=account("admin");String revoked=token(id);jdbc.update("UPDATE `user` SET status='disabled' WHERE id=?",id);
        api("GET","/admin/users",null,revoked).andExpect(status().isUnauthorized());
        api("PATCH","/me/profile",body("name","Rejected"),revoked).andExpect(status().isUnauthorized());
    }
    @Test void profileReadsStoredAvatarAndKeepsItWhenEditingMetadata() throws Exception {
        long id=account("user");String actor=token(id);
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",output);
        JsonNode uploaded=ok(mvc.perform(multipart("/api/v1/me/avatar")
                .file(new org.springframework.mock.web.MockMultipartFile("file","avatar.png","image/png",output.toByteArray()))
                .header("Authorization",actor).with(request->{request.setMethod("PUT");return request;})));
        String avatar=uploaded.path("avatar").asText();assertTrue(avatar.startsWith("data:image/png;base64,"));
        assertEquals(avatar,read("/me/profile",actor).path("avatar").asText());
        assertEquals(avatar,ok(api("PATCH","/me/profile",body("name","Avatar retained"),actor)).path("avatar").asText());
        java.nio.file.Files.delete(storage.resolve(users.selectById(id).getAvatar()));
        assertEquals("",read("/me/profile",actor).path("avatar").asText());
    }
    @Test void emailCannotCollideWithAnotherAccountsEmailShapedLoginName() throws Exception {
        long first=account("user"),second=account("user");String alias="login"+unique().replace("-","")+"@x.cn";
        jdbc.update("UPDATE `user` SET username=? WHERE id=?",alias,second);
        api("PATCH","/me/profile",body("email",alias),token(first)).andExpect(status().isConflict());
    }
    @Test void privatePaperAuditFailureBlocksAdminListAfterScopeAudit() throws Exception {
        long owner=account("user");long source=paper("private",token(owner));
        doThrow(new DataAccessResourceFailureException("simulated private resource audit failure")).when(audit)
                .append(eq(101L),eq("敏感数据访问"),startsWith("paperId="+source+";"),anyString(),any());
        api("GET","/admin/users/"+owner+"/papers",null,admin).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.data").isEmpty());
        assertTrue(value("SELECT COUNT(*) FROM operation_log WHERE target=?","userId="+owner+";ownerId="+owner+";action=adminUserPapers")>0);
    }
}
