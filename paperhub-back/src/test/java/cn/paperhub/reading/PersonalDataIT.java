package cn.paperhub.reading;

import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.service.PaperWriteSupport;
import cn.paperhub.reading.entity.NoteEntity;
import cn.paperhub.reading.mapper.NoteMapper;
import cn.paperhub.reading.service.PersonalPaperAccess;
import cn.paperhub.support.SharingSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.ResultActions;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="paperhub.storage.cleanup-enabled=false")
@AutoConfigureMockMvc
class PersonalDataIT extends SharingSupport {
    @SpyBean NoteMapper notes;
    @SpyBean PersonalPaperAccess access;
    @SpyBean PaperWriteSupport writes;
    @AfterEach void clearPersonalSpies() { reset(notes,access,writes); }
    long published() throws Exception { return publish(base("shared"),user); }
    ResultActions favorite(long id,Boolean target,String actor) throws Exception {
        return api("POST","/papers/"+id+"/favorite",target==null?body():body("favorited",target),actor);
    }
    ResultActions addReading(long id,String actor) throws Exception { return api("POST","/papers/"+id+"/reading",null,actor); }
    ResultActions setReadingStatus(long id,String status,String actor) throws Exception {
        return api("PATCH","/me/reading-records/"+id,body("status",status),actor);
    }
    JsonNode note(long paper,String title,String content,String actor) throws Exception {
        return ok(api("POST","/me/notes",body("paperId",paper,"title",title,"content",content),actor));
    }
    boolean containsPaper(JsonNode list,long id) {
        for(JsonNode item:list) if(item.path("paperId").asLong()==id)return true;
        return false;
    }
    @Test void favoritesAreIsolatedAndExplicitTargetsAreIdempotent() throws Exception {
        long id=published();
        ok(favorite(id,true,user));JsonNode first=read("/me/favorites",user);
        JsonNode row=null;for(JsonNode item:first)if(item.path("paperId").asLong()==id)row=item;
        assertNotNull(row);String timestamp=row.path("favoritedAt").asText();
        assertEquals(id,row.path("paper").path("id").asLong());assertFalse(row.path("paper").has("filePath"));
        ok(favorite(id,true,user));assertEquals(1,value("SELECT COUNT(*) FROM favorite WHERE user_id=1 AND paper_id=?",id));
        assertEquals(timestamp,jdbc.queryForObject("SELECT DATE_FORMAT(favorited_at,'%Y-%m-%d %H:%i:%s') FROM favorite WHERE user_id=1 AND paper_id=?",String.class,id));
        assertFalse(containsPaper(read("/me/favorites?userId=1",other),id));
        ok(favorite(id,true,other));ok(favorite(id,false,user));
        assertTrue(containsPaper(read("/me/favorites",other),id));assertFalse(containsPaper(read("/me/favorites",user),id));
    }
    @Test void favoritesOnlyAcceptApprovedSharedPapers() throws Exception {
        long privateId=paper("private",user),pending=paper("shared",user),id=published();
        favorite(privateId,true,user).andExpect(status().isBadRequest());
        favorite(privateId,true,other).andExpect(status().isNotFound());
        favorite(pending,true,user).andExpect(status().isBadRequest());
        favorite(pending,true,other).andExpect(status().isNotFound());
        ok(favorite(id,true,other));ok(flow(id,"unshare",null,user));
        assertFalse(containsPaper(read("/me/favorites",other),id));
        favorite(id,true,other).andExpect(status().isNotFound());
        ok(favorite(id,false,other));assertEquals(0,value("SELECT COUNT(*) FROM favorite WHERE user_id=2 AND paper_id=?",id));
        assertFalse(ok(favorite(9007199254740991L,false,user)).path("favorited").asBoolean());
    }
    @Test void unfavoritingDoesNotDeleteReadingsOrNotesEvenAfterPaperDeletion() throws Exception {
        long id=published();ok(favorite(id,true,other));ok(setReadingStatus(id,"read",other));
        long noteId=note(id,"Retained "+unique(),"Keep this body",other).path("id").asLong();
        ok(api("DELETE","/admin/papers/"+id,null,admin));
        assertFalse(containsPaper(read("/me/favorites",other),id));
        assertFalse(containsPaper(read("/me/reading-records",other),id));
        assertEquals(1,value("SELECT COUNT(*) FROM favorite WHERE user_id=2 AND paper_id=?",id));
        ok(favorite(id,false,other));
        assertEquals(1,value("SELECT COUNT(*) FROM reading_record WHERE user_id=2 AND paper_id=?",id));
        assertEquals(1,value("SELECT COUNT(*) FROM note WHERE id=? AND user_id=2",noteId));
    }
    @Test void concurrentFavoriteTargetsAndTogglesAreSerialized() throws Exception {
        long id=published();ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            for(Boolean target:new Boolean[]{true,null}) {
                if(target==null)ok(favorite(id,false,user));
                CountDownLatch start=new CountDownLatch(1);
                Callable<Boolean> call=()->{start.await();return ok(favorite(id,target,user)).path("favorited").asBoolean();};
                Future<Boolean> a=pool.submit(call),b=pool.submit(call);start.countDown();
                boolean first=a.get(10,TimeUnit.SECONDS),second=b.get(10,TimeUnit.SECONDS);
                if(target!=null)assertTrue(first&&second);else assertNotEquals(first,second);
                assertEquals(target==null?0:1,value("SELECT COUNT(*) FROM favorite WHERE user_id=1 AND paper_id=?",id));
            }
        } finally {pool.shutdownNow();}
    }
    @Test void favoriteWriteAndRemovalRollBackIfLoggingFails() throws Exception {
        long id=published();
        doThrow(new DataAccessResourceFailureException("simulated favorite log failure")).when(audit)
                .append(eq(1L),eq("论文收藏"),anyString(),anyString(),any());
        favorite(id,true,user).andExpect(status().isInternalServerError());
        assertEquals(0,value("SELECT COUNT(*) FROM favorite WHERE user_id=1 AND paper_id=?",id));
        reset(audit);ok(favorite(id,true,user));
        doThrow(new DataAccessResourceFailureException("simulated removal log failure")).when(audit)
                .append(eq(1L),eq("取消收藏"),anyString(),anyString(),any());
        favorite(id,false,user).andExpect(status().isInternalServerError());
        assertEquals(1,value("SELECT COUNT(*) FROM favorite WHERE user_id=1 AND paper_id=?",id));
    }
    @Test void readingsAllowOwnPrivatePapersButNotAnotherUsersHiddenPaper() throws Exception {
        long id=paper("private",user);
        JsonNode created=ok(addReading(id,user));assertEquals("unread",created.path("status").asText());
        assertEquals(4,created.size());assertTrue(created.path("addedAt").asText().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        addReading(id,other).andExpect(status().isNotFound());
        setReadingStatus(id,"read",other).andExpect(status().isNotFound());
        assertTrue(containsPaper(read("/me/reading-records",user),id));
        assertFalse(containsPaper(read("/me/reading-records?userId=1",other),id));
        assertEquals(0,value("SELECT COUNT(*) FROM reading_record WHERE paper_id=? AND user_id=2",id));
    }
    @Test void repeatedAddPreservesStatusAddedTimeAndLastReadTime() throws Exception {
        long id=published();ok(addReading(id,other));ok(setReadingStatus(id,"read",other));
        jdbc.update("UPDATE reading_record SET added_at='2025-01-02 03:04:05',last_read_at='2025-06-07 08:09:10' WHERE paper_id=? AND user_id=2",id);
        JsonNode result=ok(addReading(id,other));
        assertEquals("read",result.path("status").asText());
        assertEquals("2025-01-02 03:04:05",result.path("addedAt").asText());
        assertEquals("2025-06-07 08:09:10",result.path("lastReadAt").asText());
        assertEquals(1,value("SELECT COUNT(*) FROM reading_record WHERE paper_id=? AND user_id=2",id));
    }
    @Test void statusPatchCreatesMissingRecordAndUsersRemainIndependent() throws Exception {
        long id=published();
        JsonNode direct=ok(setReadingStatus(id,"paused",other));assertEquals("paused",direct.path("status").asText());
        ok(addReading(id,user));assertEquals("unread",jdbc.queryForObject("SELECT status FROM reading_record WHERE paper_id=? AND user_id=1",String.class,id));
        jdbc.update("UPDATE reading_record SET added_at='2025-01-02 03:04:05',last_read_at='2025-01-02 03:04:05' WHERE paper_id=? AND user_id=2",id);
        for(String state:Arrays.asList("unread","reading","read","paused")) {
            JsonNode current=ok(setReadingStatus(id,state,other));assertEquals(state,current.path("status").asText());
            assertEquals("2025-01-02 03:04:05",current.path("addedAt").asText());
            assertNotEquals("2025-01-02 03:04:05",current.path("lastReadAt").asText());
        }
    }
    @Test void concurrentAddAndPatchNeverResetAReadingStateOrCreateDuplicates() throws Exception {
        long id=published();ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start=new CountDownLatch(1);
            Future<JsonNode> first=pool.submit(()->{start.await();return ok(addReading(id,user));});
            Future<JsonNode> second=pool.submit(()->{start.await();return ok(setReadingStatus(id,"read",user));});
            start.countDown();first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
        assertEquals(1,value("SELECT COUNT(*) FROM reading_record WHERE user_id=1 AND paper_id=?",id));
        assertEquals("read",jdbc.queryForObject("SELECT status FROM reading_record WHERE user_id=1 AND paper_id=?",String.class,id));
    }
    @Test void lostVisibilityHidesConsumersReadingButUploaderKeepsTheirOwnRecord() throws Exception {
        long id=published();ok(addReading(id,user));ok(addReading(id,other));ok(flow(id,"unshare",null,user));
        assertTrue(containsPaper(read("/me/reading-records",user),id));
        assertFalse(containsPaper(read("/me/reading-records",other),id));
        setReadingStatus(id,"read",other).andExpect(status().isNotFound());
        ok(api("DELETE","/papers/"+id,null,user));
        assertFalse(containsPaper(read("/me/reading-records",user),id));
        assertEquals(2,value("SELECT COUNT(*) FROM reading_record WHERE paper_id=?",id));
    }
    @Test void readingWritesRollBackIfTheMutationLogFails() throws Exception {
        long id=published();
        doThrow(new DataAccessResourceFailureException("simulated reading log failure")).when(audit)
                .append(eq(1L),eq("加入阅读"),anyString(),anyString(),any());
        addReading(id,user).andExpect(status().isInternalServerError());
        assertEquals(0,value("SELECT COUNT(*) FROM reading_record WHERE user_id=1 AND paper_id=?",id));
        reset(audit);ok(addReading(id,user));
        doThrow(new DataAccessResourceFailureException("simulated status log failure")).when(audit)
                .append(eq(1L),eq("阅读状态更新"),anyString(),anyString(),any());
        setReadingStatus(id,"read",user).andExpect(status().isInternalServerError());
        assertEquals("unread",jdbc.queryForObject("SELECT status FROM reading_record WHERE user_id=1 AND paper_id=?",String.class,id));
    }
    @Test void notesPreserveFormattingAndReturnOnlyTheNoteContract() throws Exception {
        long id=paper("private",user);String content="  markdown code\r\n\n<script>literal</script>  \n";
        JsonNode created=note(id,"  Title "+unique()+"  ",content,user);
        assertEquals(content,created.path("content").asText());assertTrue(created.path("paperAvailable").asBoolean());
        assertEquals(8,created.size());assertFalse(created.has("userId"));assertFalse(created.has("sourceOwnerId"));
        assertFalse(created.has("sourceVisibility"));assertFalse(created.has("abstract"));assertFalse(created.has("filePath"));
        assertEquals(created.path("createdAt"),created.path("updatedAt"));
        JsonNode listed=named(read("/me/notes",user),created.path("id").asLong());
        assertEquals(content,listed.path("content").asText());assertEquals(created.path("paperTitle"),listed.path("paperTitle"));
    }
    @Test void noteBodiesAndMutationsStayWithTheirOwnerEvenForAdmin() throws Exception {
        long paper=published();long id=note(paper,"Owned "+unique(),"Private body",user).path("id").asLong();
        assertFalse(ids(read("/me/notes?userId=1",other)).contains(id));
        assertFalse(ids(read("/me/notes?userId=1",admin)).contains(id));
        for(String token:Arrays.asList(other,admin)) {
            api("PATCH","/me/notes/"+id,body("content","steal"),token).andExpect(status().isNotFound());
            api("DELETE","/me/notes/"+id,null,token).andExpect(status().isNotFound());
        }
        assertEquals("Private body",jdbc.queryForObject("SELECT content FROM note WHERE id=?",String.class,id));
    }
    @Test void unavailablePaperKeepsTheOriginalSnapshotAndNoteCanStillBeEditedAndDeleted() throws Exception {
        long paper=published();String oldTitle=read("/papers/"+paper,user).path("title").asText();
        long id=note(paper,"Retained "+unique(),"Original note",other).path("id").asLong();
        ok(flow(paper,"unshare",null,user));String hiddenTitle="Hidden renamed "+unique();
        ok(api("PATCH","/papers/"+paper,body("title",hiddenTitle),user));
        JsonNode hidden=named(read("/me/notes",other),id);
        assertFalse(hidden.path("paperAvailable").asBoolean());assertEquals(oldTitle,hidden.path("paperTitle").asText());
        assertFalse(hidden.toString().contains(hiddenTitle));
        ok(api("DELETE","/papers/"+paper,null,user));
        JsonNode updated=ok(api("PATCH","/me/notes/"+id,body("paperId",paper,"title","Keep editing","content","Edited after deletion"),other));
        assertFalse(updated.path("paperAvailable").asBoolean());assertEquals(oldTitle,updated.path("paperTitle").asText());
        assertEquals("Edited after deletion",updated.path("content").asText());
        api("POST","/me/notes",body("paperId",paper,"title","new","content","not allowed"),other).andExpect(status().isNotFound());
        ok(api("DELETE","/me/notes/"+id,null,other));
        assertEquals(0,value("SELECT COUNT(*) FROM note WHERE id=?",id));
    }
    @Test void relinkingUsesACurrentVisibleTitleSnapshotAndPreservesCreatedAt() throws Exception {
        long first=paper("private",user),second=paper("private",user);
        JsonNode created=note(first,"Relink "+unique(),"body",user);long id=created.path("id").asLong();
        String title="Current source "+unique();ok(api("PATCH","/papers/"+second,body("title",title),user));
        JsonNode updated=ok(api("PATCH","/me/notes/"+id,body("paperId",second),user));
        assertEquals(second,updated.path("paperId").asLong());assertEquals(title,updated.path("paperTitle").asText());
        assertEquals("body",updated.path("content").asText());assertEquals(created.path("createdAt"),updated.path("createdAt"));
        assertEquals(1,value("SELECT user_id FROM note WHERE id=?",id));
    }
    @Test void invisibleNewAssociationsAreRejectedAndDoNotPartiallyChangeTheBody() throws Exception {
        long own=paper("private",user),foreign=paper("private",other),pending=paper("shared",other);
        long id=note(own,"Original title","Original content",user).path("id").asLong();
        for(long target:new long[]{foreign,pending,9007199254740991L})
            api("PATCH","/me/notes/"+id,body("paperId",target,"title","bad","content","bad"),user).andExpect(status().isNotFound());
        assertEquals(own,value("SELECT paper_id FROM note WHERE id=?",id));
        assertEquals("Original title",jdbc.queryForObject("SELECT title FROM note WHERE id=?",String.class,id));
        assertEquals("Original content",jdbc.queryForObject("SELECT content FROM note WHERE id=?",String.class,id));
    }
    @Test void noteSearchUsesTitleAndSnapshotWithLiteralWildcardsNotTheBody() throws Exception {
        long paper=published();String marker=unique();
        long first=note(paper,"Search "+marker,"body only "+unique(),user).path("id").asLong();
        String bodyOnly="BodyOnly"+unique();note(paper,"Other "+unique(),bodyOnly,user);
        long literal=note(paper,"Literal %_! "+unique(),"body",user).path("id").asLong();
        assertEquals(Collections.singleton(first),ids(queryNotes("keyword",marker.toUpperCase(Locale.ROOT),user)));
        assertEquals(Collections.singleton(literal),ids(queryNotes("keyword","%_!",user)));
        assertTrue(queryNotes("keyword",bodyOnly,user).isEmpty());
        String source=read("/papers/"+paper,user).path("title").asText();
        assertTrue(ids(queryNotes("keyword",source,user)).contains(first));
        ok(api("DELETE","/papers/"+paper,null,user));
        assertTrue(ids(queryNotes("paperId",String.valueOf(paper),user)).contains(first));
        assertFalse(named(queryNotes("paperId",String.valueOf(paper),user),first).path("paperAvailable").asBoolean());
    }
    JsonNode queryNotes(String name,String value,String token) throws Exception {
        return ok(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/me/notes")
                .header("Authorization",token).param(name,value)));
    }
    @Test void noteCreationEditAndDeletionRollBackWithTheirLogs() throws Exception {
        long paper=paper("private",user);String title="Failed "+unique();
        doThrow(new DataAccessResourceFailureException("simulated create log failure")).when(audit)
                .append(eq(1L),eq("笔记创建"),anyString(),anyString(),any());
        api("POST","/me/notes",body("paperId",paper,"title",title,"content","secret"),user).andExpect(status().isInternalServerError());
        assertEquals(0,value("SELECT COUNT(*) FROM note WHERE title=?",title));
        reset(audit);long id=note(paper,"Original","Keep body",user).path("id").asLong();
        doThrow(new DataAccessResourceFailureException("simulated edit log failure")).when(audit)
                .append(eq(1L),eq("笔记更新"),anyString(),anyString(),any());
        api("PATCH","/me/notes/"+id,body("title","Changed","content","lost"),user).andExpect(status().isInternalServerError());
        assertEquals("Keep body",jdbc.queryForObject("SELECT content FROM note WHERE id=?",String.class,id));
        reset(audit);
        doThrow(new DataAccessResourceFailureException("simulated delete log failure")).when(audit)
                .append(eq(1L),eq("笔记删除"),anyString(),anyString(),any());
        api("DELETE","/me/notes/"+id,null,user).andExpect(status().isInternalServerError());
        assertEquals(1,value("SELECT COUNT(*) FROM note WHERE id=?",id));
    }
    @Test void mutationLogsContainIdentifiersAndNeverTheNoteText() throws Exception {
        long paper=paper("private",user);String secret="PrivateSecret"+unique();
        long id=note(paper,secret,secret+" body",user).path("id").asLong();
        ok(api("PATCH","/me/notes/"+id,body("content",secret+" edited"),user));
        ok(api("DELETE","/me/notes/"+id,null,user));
        assertEquals(0,value("SELECT COUNT(*) FROM operation_log WHERE target LIKE ?","%"+secret+"%"));
        assertEquals(3,value("SELECT COUNT(*) FROM operation_log WHERE target LIKE ?","noteId="+id+";%"));
    }
    @Test void adminCanLinkPrivatePaperButOwnRecordReadsStillNeedAnAudit() throws Exception {
        long paper=paper("private",user);
        long id=note(paper,"Admin own note","admin text",admin).path("id").asLong();
        ok(addReading(paper,admin));
        assertTrue(value("SELECT COUNT(*) FROM operation_log WHERE action='敏感数据访问' AND target LIKE ?","paperId="+paper+";%")>=2);
        assertTrue(ids(read("/me/notes",admin)).contains(id));
        assertTrue(containsPaper(read("/me/reading-records",admin),paper));
        doThrow(new DataAccessResourceFailureException("simulated private source audit outage")).when(audit)
                .append(eq(101L),eq("敏感数据访问"),anyString(),anyString(),any());
        api("GET","/me/notes",null,admin).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.data").isEmpty());
        api("GET","/me/reading-records",null,admin).andExpect(status().isInternalServerError());
        api("POST","/me/notes",body("paperId",paper,"title","blocked","content","blocked"),admin).andExpect(status().isInternalServerError());
        assertEquals(1,value("SELECT COUNT(*) FROM note WHERE user_id=101 AND paper_id=?",paper));
    }
    @Test void aChangedAssociationReturnsConflictInsteadOfApplyingAStalePatch() throws Exception {
        long first=paper("private",user),second=paper("private",user),third=paper("private",user);
        long id=note(first,"Original","old",user).path("id").asLong();AtomicBoolean once=new AtomicBoolean();
        doAnswer(invocation->{
            long current=value("SELECT paper_id FROM note WHERE id=? AND user_id=1",id);
            NoteEntity reference=new NoteEntity();reference.setId(id);reference.setUserId(1L);reference.setPaperId(current);
            if(once.compareAndSet(false,true))
                CompletableFuture.runAsync(()->{
                    try {ok(api("PATCH","/me/notes/"+id,body("paperId",second,"content","inner body"),user));}
                    catch(Exception ex){throw new CompletionException(ex);}
                }).get(5,TimeUnit.SECONDS);
            return reference;
        }).when(notes).reference(id,1L);
        api("PATCH","/me/notes/"+id,body("paperId",third,"title","outer title","content","outer body"),user)
                .andExpect(status().isConflict());
        assertEquals(second,value("SELECT paper_id FROM note WHERE id=?",id));
        assertEquals("inner body",jdbc.queryForObject("SELECT content FROM note WHERE id=?",String.class,id));
    }
    @Test void aPartialPatchMergesWithAConcurrentBodyEditUsingTheCurrentRow() throws Exception {
        long paper=paper("private",user),id=note(paper,"Original","old",user).path("id").asLong();
        AtomicBoolean once=new AtomicBoolean();
        doAnswer(invocation->{
            long current=value("SELECT paper_id FROM note WHERE id=? AND user_id=1",id);
            NoteEntity reference=new NoteEntity();reference.setId(id);reference.setUserId(1L);reference.setPaperId(current);
            if(once.compareAndSet(false,true))
                CompletableFuture.runAsync(()->{
                    try {ok(api("PATCH","/me/notes/"+id,body("content","concurrent body"),user));}
                    catch(Exception ex){throw new CompletionException(ex);}
                }).get(5,TimeUnit.SECONDS);
            return reference;
        }).when(notes).reference(id,1L);
        JsonNode result=ok(api("PATCH","/me/notes/"+id,body("title","new title"),user));
        assertEquals("new title",result.path("title").asText());assertEquals("concurrent body",result.path("content").asText());
    }
    @Test void simultaneousCrossPaperRelinksUseAscendingPaperLocks() throws Exception {
        long a=paper("private",user),b=paper("private",user);
        long first=note(a,"A","A body",user).path("id").asLong(),second=note(b,"B","B body",user).path("id").asLong();
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start=new CountDownLatch(1);
            Future<JsonNode> one=pool.submit(()->{start.await();return ok(api("PATCH","/me/notes/"+first,body("paperId",b),user));});
            Future<JsonNode> two=pool.submit(()->{start.await();return ok(api("PATCH","/me/notes/"+second,body("paperId",a),user));});
            start.countDown();assertEquals(b,one.get(10,TimeUnit.SECONDS).path("paperId").asLong());
            assertEquals(a,two.get(10,TimeUnit.SECONDS).path("paperId").asLong());
        } finally {pool.shutdownNow();}
    }
    @Test void personalWritesRecheckVisibilityAfterWaitingForWithdrawal() throws Exception {
        for(String operation:Arrays.asList("favorite","reading","note")) {
            long paper=published();
            CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1),waiting=new CountDownLatch(1);
            doAnswer(invocation->{
                Object current=invocation.callRealMethod();held.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return current;
            }).when(writes).active(paper);
            doAnswer(invocation->{waiting.countDown();return invocation.callRealMethod();}).when(access).lock(paper);
            ExecutorService pool=Executors.newFixedThreadPool(2);
            try {
                Future<JsonNode> withdrawal=pool.submit(()->ok(flow(paper,"unshare",null,user)));
                assertTrue(held.await(5,TimeUnit.SECONDS));
                Future<Integer> attempt=pool.submit(()->{
                    ResultActions response;
                    if("favorite".equals(operation))response=favorite(paper,true,other);
                    else if("reading".equals(operation))response=addReading(paper,other);
                    else response=api("POST","/me/notes",body("paperId",paper,"title","blocked","content","blocked"),other);
                    return response.andReturn().getResponse().getStatus();
                });
                assertTrue(waiting.await(5,TimeUnit.SECONDS));release.countDown();
                withdrawal.get(10,TimeUnit.SECONDS);assertEquals(404,attempt.get(10,TimeUnit.SECONDS).intValue());
            } finally {release.countDown();pool.shutdownNow();}
            for(String table:Arrays.asList("favorite","reading_record","note"))
                assertEquals(0,value("SELECT COUNT(*) FROM "+table+" WHERE paper_id=? AND user_id=2",paper));
            reset(writes,access);
        }
    }
    @Test void strictDtosRejectIdentityTimeAndSnapshotSpoofing() throws Exception {
        long paper=published(),id=note(paper,"Original","body",user).path("id").asLong();
        for(com.fasterxml.jackson.databind.node.ObjectNode invalid:Arrays.asList(body("paperId",paper,"title","t","content","c","userId",2),
                body("paperId",paper,"title","t","content","c","paperTitle","forged"),
                body("paperId",paper,"title","t","content","c","createdAt","2020-01-01 00:00:00")))
            api("POST","/me/notes",invalid,user).andExpect(status().isUnprocessableEntity());
        for(com.fasterxml.jackson.databind.node.ObjectNode invalid:Arrays.asList(body(),body("paperId",null),
                body("title",null),body("content",null),body("userId",2),body("updatedAt","x")))
            api("PATCH","/me/notes/"+id,invalid,user).andExpect(status().isUnprocessableEntity());
        api("POST","/papers/"+paper+"/favorite",body("favorited",null),user).andExpect(status().isUnprocessableEntity());
        api("POST","/papers/"+paper+"/favorite",body("userId",2,"favorited",true),user).andExpect(status().isUnprocessableEntity());
        api("POST","/papers/"+paper+"/reading",body("status","read"),user).andExpect(status().isUnprocessableEntity());
        for(String state:Arrays.asList("finished","all","", "READ"))
            setReadingStatus(paper,state,user).andExpect(status().isUnprocessableEntity());
        api("PATCH","/me/reading-records/"+paper,body("status","read","addedAt","x"),user).andExpect(status().isUnprocessableEntity());
        api("GET","/me/notes?paperId=0",null,user).andExpect(status().isUnprocessableEntity());
    }
    @Test void invalidNoteTextIsRejectedBeforeAnyRowIsCreated() throws Exception {
        long paper=paper("private",user);long before=value("SELECT COUNT(*) FROM note WHERE paper_id=?",paper);
        for(String title:Arrays.asList("", " \u3000",String.join("",Collections.nCopies(301,"x"))))
            api("POST","/me/notes",body("paperId",paper,"title",title,"content","body"),user).andExpect(status().isUnprocessableEntity());
        String tooLong=String.join("",Collections.nCopies(20001,"文"));
        api("POST","/me/notes",body("paperId",paper,"title","title","content",tooLong),user).andExpect(status().isUnprocessableEntity());
        api("POST","/me/notes",body("paperId",paper,"title","title","content"," \u3000\n"),user).andExpect(status().isUnprocessableEntity());
        assertEquals(before,value("SELECT COUNT(*) FROM note WHERE paper_id=?",paper));
    }
    @Test void anonymousAndDisabledUsersCannotReachPersonalDataOrWriteIt() throws Exception {
        long paper=paper("private",user),id=note(paper,"Own","body",user).path("id").asLong();
        for(String path:Arrays.asList("/me/favorites","/me/reading-records","/me/notes"))
            api("GET",path,null,null).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE `user` SET status='disabled' WHERE id=1");
        api("PATCH","/me/notes/"+id,body("content","blocked"),user).andExpect(status().isUnauthorized());
        favorite(paper,false,user).andExpect(status().isUnauthorized());
        addReading(paper,user).andExpect(status().isUnauthorized());
        assertEquals("body",jdbc.queryForObject("SELECT content FROM note WHERE id=?",String.class,id));
    }
}

