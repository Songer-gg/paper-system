package cn.paperhub.paper;

import cn.paperhub.support.SharingSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="paperhub.storage.cleanup-enabled=false")
@AutoConfigureMockMvc
class SharedWorkflowIT extends SharingSupport {
    @Test void privateShareRequiresConsentAndAlwaysEntersPending() throws Exception {
        long id=paper("private",user);
        flow(id,"share",null,user).andExpect(status().isBadRequest());
        flow(id,"share",false,user).andExpect(status().isBadRequest());
        JsonNode result=ok(flow(id,"share",true,user));
        assertEquals("shared",result.path("visibility").asText()); assertEquals("pending",state(id));
        assertEquals(1,value("SELECT copyright_agreed FROM paper WHERE id=?",id));
        assertNotNull(jdbc.queryForObject("SELECT copyright_agreed_at FROM paper WHERE id=?",java.time.LocalDateTime.class,id));
        api("GET","/shared-papers/"+id,null,user).andExpect(status().isNotFound());
        assertEquals(0,value("SELECT COUNT(*) FROM review_record WHERE paper_id=?",id)); freeLock();
    }
    @Test void shareCannotPublishPersonalClassificationWithoutExplicitMetadataChanges() throws Exception {
        long category=ok(api("POST","/categories",body("name",unique(),"parentId",null),user)).path("id").asLong();
        long tag=ok(api("POST","/tags",body("name",unique(),"color","#123456"),user)).path("id").asLong();
        ObjectNode payload=base("private"); payload.put("categoryId",category); payload.putArray("tagIds").add(tag);
        long id=create(payload,user);
        flow(id,"share",true,user).andExpect(status().isBadRequest());
        assertEquals("private",jdbc.queryForObject("SELECT visibility FROM paper WHERE id=?",String.class,id));
        ok(api("PATCH","/papers/"+id,body("categoryId",1,"tagIds",Collections.emptyList()),user));
        ok(flow(id,"share",true,user)); assertEquals("pending",state(id));
    }
    @Test void userFlowIsRestrictedToTheUploaderIncludingForAdministrators() throws Exception {
        long id=paper("private",user);
        flow(id,"share",true,other).andExpect(status().isNotFound());
        flow(id,"share",true,admin).andExpect(status().isForbidden());
        ok(flow(id,"share",true,user)); ok(approve(id));
        flow(id,"unshare",null,other).andExpect(status().isForbidden());
        flow(id,"unshare",null,admin).andExpect(status().isForbidden());
    }
    @Test void approvePersistsReviewAndMakesThePaperPublicOnlyAfterCommit() throws Exception {
        long id=paper("shared",user);
        api("GET","/shared-papers/"+id,null,other).andExpect(status().isNotFound());
        ok(approve(id));
        JsonNode view=read("/shared-papers/"+id,other);
        assertEquals("approved",view.path("reviewStatus").asText());
        JsonNode record=null;
        for(JsonNode item:read("/admin/review/records?action=approved",admin))
            if(item.path("paperId").asLong()==id) record=item;
        assertNotNull(record); assertEquals("林知远",record.path("uploaderName").asText());
        assertEquals("平台管理员",record.path("reviewerName").asText());
        assertEquals("reviewed",record.path("comment").asText()); freeLock();
    }
    @Test void approveRequiresBothUserConsentAndExplicitAdministratorConfirmation() throws Exception {
        long id=paper("shared",user);
        api("POST","/admin/review/papers/"+id+"/approve",body("copyrightConfirmed",false),admin)
                .andExpect(status().isBadRequest());
        api("POST","/admin/review/papers/"+id+"/approve",body("comment","missing checkbox"),admin)
                .andExpect(status().isBadRequest());
        jdbc.update("UPDATE paper SET copyright_agreed=0,copyright_agreed_at=NULL WHERE id=?",id);
        approve(id).andExpect(status().isBadRequest()); assertEquals("pending",state(id));
        assertEquals(0,value("SELECT COUNT(*) FROM review_record WHERE paper_id=?",id));
    }
    @Test void withdrawRetainsTheSharedRecordAndCannotBeRestoredByAdmin() throws Exception {
        long id=publish(base("shared"),user);
        JsonNode withdrawn=ok(flow(id,"unshare",null,user));
        assertEquals("shared",withdrawn.path("visibility").asText());
        assertEquals("offline",withdrawn.path("reviewStatus").asText());
        assertEquals("withdrawn",withdrawn.path("offlineReason").asText());
        assertTrue(ids(read("/papers/mine",user).path("sharedPapers")).contains(id));
        api("PATCH","/admin/papers/"+id+"/restore",null,admin).andExpect(status().isBadRequest());
        api("GET","/shared-papers/"+id,null,other).andExpect(status().isNotFound());
        ok(flow(id,"resubmit",null,user)); assertEquals("pending",state(id));
        assertNull(jdbc.queryForObject("SELECT offline_reason FROM paper WHERE id=?",String.class,id));
        assertEquals(1,value("SELECT COUNT(*) FROM review_record WHERE paper_id=?",id)); freeLock();
    }
    @Test void moderationOfflineOnlyRestoresThroughAdminAndRevalidatesTheFile() throws Exception {
        long id=publish(base("shared"),user);
        ok(api("PATCH","/admin/papers/"+id+"/offline",null,admin));
        assertEquals("moderation",jdbc.queryForObject("SELECT offline_reason FROM paper WHERE id=?",String.class,id));
        flow(id,"resubmit",true,user).andExpect(status().isBadRequest());
        byte[] bytes=Files.readAllBytes(storage.resolve(key(id)));
        Files.write(storage.resolve(key(id)),"tampered".getBytes(StandardCharsets.US_ASCII));
        try { api("PATCH","/admin/papers/"+id+"/restore",null,admin).andExpect(status().isBadRequest()); }
        finally { Files.write(storage.resolve(key(id)),bytes); }
        ok(api("PATCH","/admin/papers/"+id+"/restore",null,admin));
        assertEquals("approved",state(id));
    }
    @Test void legacyRejectedSubmissionNeedsFreshConsentAndMissingPdfCannotBePublished() throws Exception {
        long id=paper("shared",user);
        jdbc.update("UPDATE paper SET review_status='rejected',copyright_agreed=0,copyright_agreed_at=NULL WHERE id=?",id);
        flow(id,"resubmit",null,user).andExpect(status().isBadRequest());
        ok(flow(id,"resubmit",true,user)); assertEquals("pending",state(id));
        String original=key(id); jdbc.update("UPDATE paper SET file_path='/storage/missing.pdf' WHERE id=?",id);
        try { approve(id).andExpect(status().isBadRequest()); assertEquals("pending",state(id)); }
        finally { jdbc.update("UPDATE paper SET file_path=? WHERE id=?",original,id); }
    }
    @Test void rejectRequiresReasonAndResubmissionDoesNotEraseTheReviewSnapshot() throws Exception {
        long id=paper("shared",user);
        api("POST","/admin/review/papers/"+id+"/reject",body("comment"," "),admin).andExpect(status().isBadRequest());
        api("POST","/admin/review/papers/"+id+"/reject",body("comment","\u3000"),admin).andExpect(status().isBadRequest());
        ok(api("POST","/admin/review/papers/"+id+"/reject",body("comment","Needs correction"),admin));
        assertEquals("rejected",state(id));
        assertEquals("Needs correction",read("/papers/"+id,user).path("reviewComment").asText());
        ok(flow(id,"resubmit",null,user)); assertEquals("pending",state(id));
        assertFalse(read("/papers/"+id,user).has("reviewComment"));
        assertEquals("Needs correction",jdbc.queryForObject("SELECT comment FROM review_record WHERE paper_id=?",String.class,id));
    }
    @Test void duplicateHintsNeverExposeHiddenOrPrivatePapers() throws Exception {
        String doi="10.9999/"+unique(); ObjectNode p=base("shared"); p.put("doi",doi);
        long id=create(p,user);
        JsonNode hidden=ok(api("POST","/papers/duplicate-check",body("doi","https://doi.org/"+doi.toUpperCase(Locale.ROOT)),other));
        assertEquals("doi",hidden.path("level").asText()); assertFalse(hidden.has("paper"));
        assertEquals(id,ok(api("POST","/papers/duplicate-check",body("doi",doi),user)).path("paper").path("id").asLong());
        assertEquals(id,ok(api("POST","/papers/duplicate-check",body("doi",doi),admin)).path("paper").path("id").asLong());
        ok(approve(id));
        assertEquals(id,ok(api("POST","/papers/duplicate-check",body("doi",doi),other)).path("paper").path("id").asLong());
        ObjectNode privatePaper=base("private"); privatePaper.put("doi","10.9999/"+unique());
        create(privatePaper,user);
        assertEquals("none",ok(api("POST","/papers/duplicate-check",body("doi",privatePaper.path("doi").asText()),other)).path("level").asText());
    }
    @Test void fileAndTitleHintsUseTheDocumentHashAndNormalizedTitle() throws Exception {
        String title="Title "+unique(); long id=titled(title,user);
        String hash=jdbc.queryForObject("SELECT file_sha256 FROM paper WHERE id=?",String.class,id);
        assertEquals("file",ok(api("POST","/papers/duplicate-check",body("fileSha256",hash.toUpperCase(Locale.ROOT)),other)).path("level").asText());
        JsonNode hint=ok(api("POST","/papers/duplicate-check",body("title",title.toUpperCase(Locale.ROOT)),other));
        assertEquals("title",hint.path("level").asText()); assertFalse(hint.has("paper"));
        assertEquals("none",ok(api("POST","/papers/duplicate-check",body(),user)).path("level").asText());
    }
    @Test void exactDuplicateSharesAreSerializedAndLeaveTheLoserPrivate() throws Exception {
        String doi="10.9999/"+unique(); byte[] bytes=pdf();
        ObjectNode one=base("private"),two=base("private"); one.put("doi",doi); two.put("doi",doi);
        long a=ok(upload(one,bytes,user)).path("id").asLong(),b=ok(upload(two,bytes,other)).path("id").asLong();
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start=new CountDownLatch(1);
            Future<Integer> first=pool.submit(()->{start.await();return flow(a,"share",true,user).andReturn().getResponse().getStatus();});
            Future<Integer> second=pool.submit(()->{start.await();return flow(b,"share",true,other).andReturn().getResponse().getStatus();});
            start.countDown();
            List<Integer> codes=Arrays.asList(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
            Collections.sort(codes); assertEquals(Arrays.asList(200,409),codes);
        } finally { pool.shutdownNow(); }
        assertEquals(1,value("SELECT COUNT(*) FROM paper WHERE id IN (?,?) AND visibility='shared'",a,b));
        assertEquals(1,value("SELECT COUNT(*) FROM paper WHERE id IN (?,?) AND visibility='private' AND review_status IS NULL",a,b)); freeLock();
    }
    @Test void concurrentSimilarUploadsCreateOnePairAndBlockApprovalUntilManualDecision() throws Exception {
        String title="Same "+unique(); byte[] firstFile=pdf(),secondFile=pdf();
        ObjectNode first=base("shared"),second=base("shared"); first.put("title",title); second.put("title",title);
        ExecutorService pool=Executors.newFixedThreadPool(2); long a,b;
        try {
            Future<Long> one=pool.submit(()->ok(upload(first,firstFile,user)).path("id").asLong());
            Future<Long> two=pool.submit(()->ok(upload(second,secondFile,other)).path("id").asLong());
            a=one.get(10,TimeUnit.SECONDS); b=two.get(10,TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(1,value("SELECT COUNT(*) FROM duplicate_pair WHERE (original_paper_id=? AND new_paper_id=?) "+
                "OR (original_paper_id=? AND new_paper_id=?)",a,b,b,a));
        approve(a).andExpect(status().isBadRequest()); approve(b).andExpect(status().isBadRequest());
        long pair=pair(a,b);
        ok(api("POST","/admin/duplicates/"+pair+"/distinct",body("note","Different actual documents"),admin));
        assertEquals("pending",state(a)); assertEquals("pending",state(b));
        ok(approve(a)); ok(approve(b)); freeLock();
    }
    @Test void approvingMissingEvidencePersistsThePairDespiteTheBusinessRejection() throws Exception {
        String title="Evidence "+unique(); long one=titled(title,user),two=titled(title,other);
        jdbc.update("DELETE FROM duplicate_pair WHERE id=?",pair(one,two));
        approve(two).andExpect(status().isBadRequest());
        assertEquals("pending",state(two)); long pair=pair(one,two);
        assertEquals("pending",jdbc.queryForObject("SELECT status FROM duplicate_pair WHERE id=?",String.class,pair));
        assertEquals(0,value("SELECT COUNT(*) FROM review_record WHERE paper_id=?",two));
        assertEquals(1,value("SELECT COUNT(*) FROM operation_log WHERE action='论文审核' AND result='失败' AND target=?",
                "paperId="+two+";reason=pendingDuplicate"));
        freeLock();
    }
    @Test void sharingAnOlderPrivateIdDoesNotReverseWhichPaperIsTheNewSubmission() throws Exception {
        String title="Older private "+unique(); ObjectNode p=base("private"); p.put("title",title);
        long incoming=create(p,user),existing=titled(title,other);
        ok(flow(incoming,"share",true,user)); long pair=pair(incoming,existing);
        assertEquals(incoming,value("SELECT new_paper_id FROM duplicate_pair WHERE id=?",pair));
        assertEquals(existing,value("SELECT original_paper_id FROM duplicate_pair WHERE id=?",pair));
        ok(api("POST","/admin/duplicates/"+pair+"/confirm",body("note","Confirmed duplicate"),admin));
        assertEquals("rejected",state(incoming)); assertEquals("pending",state(existing));
        flow(incoming,"resubmit",true,user).andExpect(status().isConflict());
    }
    @Test void linkedResolutionUsesAnApprovedOriginalAndRetainsAnImmutableHistory() throws Exception {
        String title="Link "+unique(); ObjectNode p=base("shared");p.put("title",title);
        long original=publish(p,user),newer=titled(title,other),pair=pair(original,newer);
        ok(api("POST","/admin/duplicates/"+pair+"/link",body("note","Use the original"),admin));
        assertEquals("rejected",state(newer));
        JsonNode record=named(read("/admin/duplicate-records",admin),pair);
        assertEquals("linked",record.path("action").asText()); assertEquals("平台管理员",record.path("handlerName").asText());
        assertEquals(title,record.path("paperTitle").asText());
        ok(api("PATCH","/papers/"+newer,body("title","Renamed "+unique()),other));
        ok(api("DELETE","/admin/papers/"+newer,null,admin));
        assertEquals(title,named(read("/admin/duplicate-records",admin),pair).path("paperTitle").asText());
        boolean preserved=false;
        for(JsonNode review:read("/admin/review/records?action=rejected",admin))
            if(review.path("paperId").asLong()==newer) { assertEquals(title,review.path("paperTitle").asText()); preserved=true; }
        assertTrue(preserved); api("GET","/admin/papers/"+newer,null,admin).andExpect(status().isNotFound());
        api("POST","/admin/duplicates/"+pair+"/distinct",body("note","cannot overwrite"),admin).andExpect(status().isConflict());
    }
    @Test void linkRequiresAnAvailableApprovedOriginalAndExactEvidenceCannotBeMarkedDistinct() throws Exception {
        String title="Link restrictions "+unique(); long a=titled(title,user),b=titled(title,other),pair=pair(a,b);
        api("POST","/admin/duplicates/"+pair+"/link",body("note","not approved"),admin).andExpect(status().isBadRequest());
        String firstHash=jdbc.queryForObject("SELECT file_sha256 FROM paper WHERE id=?",String.class,a);
        String secondHash=jdbc.queryForObject("SELECT file_sha256 FROM paper WHERE id=?",String.class,b);
        jdbc.update("UPDATE paper SET review_status='rejected',file_sha256=? WHERE id=?",firstHash,b);
        try {
            api("POST","/admin/duplicates/"+pair+"/distinct",body("note","same bytes"),admin).andExpect(status().isBadRequest());
            assertEquals("pending",jdbc.queryForObject("SELECT status FROM duplicate_pair WHERE id=?",String.class,pair));
        } finally { jdbc.update("UPDATE paper SET file_sha256=? WHERE id=?",secondHash,b); }
    }
    @Test void duplicateResolutionDoesNotOverrideAnUploadersWithdrawnState() throws Exception {
        String title="Withdrawn history "+unique(); long a=titled(title,user),b=titled(title,other),pair=pair(a,b);
        ok(flow(b,"unshare",null,other));
        ok(api("POST","/admin/duplicates/"+pair+"/confirm",body("note","history decision"),admin));
        assertEquals("offline",state(b));
        assertEquals("withdrawn",jdbc.queryForObject("SELECT offline_reason FROM paper WHERE id=?",String.class,b));
        assertEquals(0,value("SELECT COUNT(*) FROM review_record WHERE paper_id=?",b));
    }
    @Test void duplicateResolutionCanCloseDeletedHistoryWithoutRevivingThePaper() throws Exception {
        String title="Deleted history "+unique(); long a=titled(title,user),b=titled(title,other),pair=pair(a,b);
        ok(api("DELETE","/admin/papers/"+b,null,admin));
        ok(api("POST","/admin/duplicates/"+pair+"/confirm",body("note","historical duplicate"),admin));
        assertEquals("duplicate",named(read("/admin/duplicate-records",admin),pair).path("action").asText());
        assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM paper WHERE id=?",java.time.LocalDateTime.class,b));
        api("GET","/papers/"+b,null,other).andExpect(status().isNotFound());
    }
    @Test void concurrentReviewAndWithdrawalAlwaysLeaveThePaperWithdrawn() throws Exception {
        long id=paper("shared",user); ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start=new CountDownLatch(1);
            Future<Integer> review=pool.submit(()->{start.await();return approve(id).andReturn().getResponse().getStatus();});
            Future<Integer> withdrawal=pool.submit(()->{start.await();return flow(id,"unshare",null,user).andReturn().getResponse().getStatus();});
            start.countDown(); int code=review.get(10,TimeUnit.SECONDS);
            assertTrue(code==200||code==409); assertEquals(200,withdrawal.get(10,TimeUnit.SECONDS).intValue());
        } finally { pool.shutdownNow(); }
        assertEquals("offline",state(id));
        assertEquals("withdrawn",jdbc.queryForObject("SELECT offline_reason FROM paper WHERE id=?",String.class,id));
        api("PATCH","/admin/papers/"+id+"/restore",null,admin).andExpect(status().isBadRequest()); freeLock();
    }
    @Test void concurrentDuplicateVerdictsOnlyApplyOnce() throws Exception {
        String title="Verdict "+unique();long a=titled(title,user),b=titled(title,other),pair=pair(a,b);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start=new CountDownLatch(1);
            Future<Integer> one=pool.submit(()->{start.await();return api("POST","/admin/duplicates/"+pair+"/confirm",body("note","duplicate"),admin).andReturn().getResponse().getStatus();});
            Future<Integer> two=pool.submit(()->{start.await();return api("POST","/admin/duplicates/"+pair+"/distinct",body("note","distinct"),admin).andReturn().getResponse().getStatus();});
            start.countDown();List<Integer> codes=Arrays.asList(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS));
            Collections.sort(codes);assertEquals(Arrays.asList(200,409),codes);
        } finally { pool.shutdownNow(); }
        assertEquals(1,value("SELECT COUNT(*) FROM operation_log WHERE action='重复论文处理' AND target LIKE ?","pairId="+pair+";%"));freeLock();
    }
    @Test void duplicateApprovalsDoNotCreateMultipleReviewRecords() throws Exception {
        long id=paper("shared",user);ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a=pool.submit(()->approve(id).andReturn().getResponse().getStatus());
            Future<Integer> b=pool.submit(()->approve(id).andReturn().getResponse().getStatus());
            List<Integer> codes=Arrays.asList(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            Collections.sort(codes);assertEquals(Arrays.asList(200,409),codes);
        } finally {pool.shutdownNow();}
        assertEquals(1,value("SELECT COUNT(*) FROM review_record WHERE paper_id=?",id));freeLock();
    }
    @Test void finalApprovalDoesNotTrustStaleNormalizedDoiOrStoredFileContents() throws Exception {
        ObjectNode original=base("shared");String doi="10.9999/"+unique();original.put("doi",doi);
        publish(original,user);
        long id=paper("shared",other);
        jdbc.update("UPDATE paper SET doi=? WHERE id=?","https://doi.org/"+doi.toUpperCase(Locale.ROOT),id);
        approve(id).andExpect(status().isConflict());
        assertEquals("pending",state(id));
        long tampered=paper("shared",user);byte[] bytes=Files.readAllBytes(storage.resolve(key(tampered)));
        Files.write(storage.resolve(key(tampered)),"%PDF-tampered".getBytes(StandardCharsets.US_ASCII));
        try {approve(tampered).andExpect(status().isBadRequest());assertEquals("pending",state(tampered));}
        finally {Files.write(storage.resolve(key(tampered)),bytes);}
    }
    @Test void reviewRecordFailureRollsBackStateAndReleasesTheCatalogLock() throws Exception {
        long id=paper("shared",user);
        doThrow(new DataAccessResourceFailureException("simulated review insert failure")).when(reviews).insert(any(cn.paperhub.admin.entity.ReviewRecordEntity.class));
        approve(id).andExpect(status().isInternalServerError());
        assertEquals("pending",state(id));assertEquals(0,value("SELECT COUNT(*) FROM review_record WHERE paper_id=?",id));freeLock();
    }
    @Test void reviewLogFailureRollsBackItsReviewRecordAndStatus() throws Exception {
        long id=paper("shared",user);
        doThrow(new DataAccessResourceFailureException("simulated log failure")).when(audit)
                .append(eq(101L),eq("论文审核"),anyString(),anyString(),any());
        approve(id).andExpect(status().isInternalServerError());
        assertEquals("pending",state(id));assertEquals(0,value("SELECT COUNT(*) FROM review_record WHERE paper_id=?",id));freeLock();
    }
    @Test void duplicateHandlingLogFailureRollsBackTheVerdictRejectionAndReview() throws Exception {
        String title="Rollback verdict "+unique();long a=titled(title,user),b=titled(title,other),pair=pair(a,b);
        doThrow(new DataAccessResourceFailureException("simulated handling log failure")).when(audit)
                .append(eq(101L),eq("重复论文处理"),anyString(),anyString(),any());
        api("POST","/admin/duplicates/"+pair+"/confirm",body("note","rollback"),admin).andExpect(status().isInternalServerError());
        assertEquals("pending",state(b));assertEquals("pending",jdbc.queryForObject("SELECT status FROM duplicate_pair WHERE id=?",String.class,pair));
        assertEquals(0,value("SELECT COUNT(*) FROM review_record WHERE paper_id=?",b));freeLock();
    }
    @Test void duplicateDecisionPreservesADownloadCommittedAfterItsInitialSnapshot() throws Exception {
        String title="Download race "+unique();long a=titled(title,user),b=titled(title,other),pair=pair(a,b);
        cn.paperhub.admin.entity.DuplicatePairEntity initial=new cn.paperhub.admin.entity.DuplicatePairEntity();
        initial.setId(pair);initial.setOriginalPaperId(a);initial.setNewPaperId(b);
        doAnswer(invocation->{
            // Start the handler's repeatable-read snapshot before the independent download commits.
            assertEquals(1,value("SELECT COUNT(*) FROM duplicate_pair WHERE id=?",pair));
            int status=CompletableFuture.supplyAsync(()->{
                try {return api("POST","/papers/"+b+"/download",null,other).andReturn().getResponse().getStatus();}
                catch(Exception ex) {throw new CompletionException(ex);}
            }).get(5,TimeUnit.SECONDS);
            assertEquals(200,status);return initial;
        }).when(pairs).selectById(pair);
        ok(api("POST","/admin/duplicates/"+pair+"/confirm",body("note","preserve counter"),admin));
        assertEquals("rejected",state(b));assertEquals(1,value("SELECT downloads FROM paper WHERE id=?",b));freeLock();
    }
    @Test void withdrawalLogFailureLeavesTheApprovedResourceAvailable() throws Exception {
        long id=publish(base("shared"),user);
        doThrow(new DataAccessResourceFailureException("simulated flow log failure")).when(audit)
                .append(eq(1L),eq("共享流转"),anyString(),anyString(),any());
        flow(id,"unshare",null,user).andExpect(status().isInternalServerError());
        assertEquals("approved",state(id));read("/shared-papers/"+id,other);freeLock();
    }
    @Test void adminPrivateDetailsAreAuditedAndFailClosed() throws Exception {
        long id=paper("private",user);
        read("/admin/papers/"+id,admin);
        assertTrue(value("SELECT COUNT(*) FROM operation_log WHERE action='敏感数据访问' AND target LIKE ?","paperId="+id+";%")>0);
        doThrow(new DataAccessResourceFailureException("simulated audit outage")).when(audit)
                .append(eq(101L),eq("敏感数据访问"),anyString(),anyString(),any());
        api("GET","/admin/papers/"+id,null,admin).andExpect(status().isInternalServerError());
        api("GET","/admin/papers/"+id,null,user).andExpect(status().isForbidden());
    }
    @Test void allAdministrativeCommandsRequireRealAdminAndStrictPayloads() throws Exception {
        long id=paper("shared",user);
        for(String path:Arrays.asList("/admin/papers","/admin/review/papers","/admin/review/records","/admin/duplicates","/admin/duplicate-records"))
            api("GET",path,null,user).andExpect(status().isForbidden());
        api("POST","/admin/review/papers/"+id+"/approve",body("copyrightConfirmed",true),other).andExpect(status().isForbidden());
        flow(id,"approve",true,user).andExpect(status().isUnprocessableEntity());
        api("PATCH","/papers/"+id+"/review-flow",body("action","unshare","reviewStatus","approved"),user).andExpect(status().isUnprocessableEntity());
        api("POST","/admin/review/papers/"+id+"/approve",body("copyrightConfirmed",true,"reviewerId",1),admin).andExpect(status().isUnprocessableEntity());
        api("POST","/admin/review/papers/"+id+"/reject",body("comment","x","copyrightConfirmed",true),admin).andExpect(status().isUnprocessableEntity());
        api("PATCH","/admin/papers/"+id+"/offline",body("offlineReason","withdrawn"),admin).andExpect(status().isUnprocessableEntity());
        api("POST","/papers/duplicate-check",body("fileName","fake.pdf"),user).andExpect(status().isUnprocessableEntity());
        api("POST","/papers/duplicate-check",body("fileSha256","fake"),user).andExpect(status().isUnprocessableEntity());
        ObjectNode future=base("private");future.put("year",9999);
        upload(future,pdf(),user).andExpect(status().isUnprocessableEntity());
        jdbc.update("UPDATE `user` SET status='disabled' WHERE id=101");
        api("GET","/admin/papers",null,admin).andExpect(status().isUnauthorized());
    }
}

