package cn.paperhub.admin;

import cn.paperhub.admin.entity.ReportEntity;
import cn.paperhub.admin.mapper.ReportMapper;
import cn.paperhub.common.storage.LocalFileStorage;
import cn.paperhub.support.SharingSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
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
class ReportOperationsIT extends SharingSupport {
    @SpyBean ReportMapper reports;
    @AfterEach void clearReportSpy() { reset(reports); }
    long published() throws Exception { return publish(base("shared"),user); }
    long report(long paper) {
        String marker=unique();
        jdbc.update("INSERT INTO report(paper_id,paper_title,reporter_id,type,reason) "+
                "SELECT id,title,2,'content',? FROM paper WHERE id=?",marker,paper);
        return value("SELECT id FROM report WHERE reason=?",marker);
    }
    ResultActions resolve(long id,String result,boolean offline) throws Exception {
        return api("POST","/admin/reports/"+id+"/resolve",body("result",result,"offlinePaperToo",offline),admin);
    }
    String reportState(long id) { return jdbc.queryForObject("SELECT status FROM report WHERE id=?",String.class,id); }
    long version(long id) { return value("SELECT version FROM paper WHERE id=?",id); }
    long actionCount(long paper,String action) {
        return value("SELECT COUNT(*) FROM operation_log WHERE action=? AND target LIKE ?",action,"paperId="+paper+";%");
    }
    @Test void reportListsFilterStatesAndReturnSafeSnapshotFields() throws Exception {
        long paper=published(),pending=report(paper),resolved=report(paper);ok(resolve(resolved,"Archived",false));
        JsonNode row=named(read("/admin/reports?status=pending",admin),pending);
        assertEquals(9,row.size());assertEquals(users.selectById(2).getName(),row.path("reporterName").asText());
        assertTrue(row.path("createdAt").asText().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        assertFalse(row.has("handlerId"));assertFalse(row.has("result"));assertFalse(row.has("handledAt"));
        assertFalse(ids(read("/admin/reports?status=pending",admin)).contains(resolved));
        assertTrue(ids(read("/admin/reports?status=resolved",admin)).contains(resolved));
        assertTrue(ids(read("/admin/reports",admin)).containsAll(Arrays.asList(pending,resolved)));
        assertEquals(11,named(read("/admin/report-records",admin),resolved).size());
    }
    @Test void reportSnapshotSurvivesRenameAndLogicalDeletion() throws Exception {
        long paper=published(),id=report(paper);String title=read("/papers/"+paper,user).path("title").asText();
        ok(api("PATCH","/papers/"+paper,body("title","Changed "+unique()),user));
        assertEquals(title,named(read("/admin/reports",admin),id).path("paperTitle").asText());
        ok(api("DELETE","/papers/"+paper,null,user));ok(resolve(id,"History retained",true));
        JsonNode row=named(read("/admin/report-records",admin),id);assertEquals(title,row.path("paperTitle").asText());
        assertEquals("History retained",row.path("result").asText());
        api("GET","/papers/"+paper+"/file",null,admin).andExpect(status().isNotFound());
    }
    @Test void resolvingWithoutOfflinePreservesPaperAndStoresActorAndTime() throws Exception {
        long paper=published(),id=report(paper),version=version(paper);
        ok(resolve(id,"  Checked and dismissed  ",false));
        assertEquals("approved",state(paper));assertEquals(version,version(paper));assertEquals("resolved",reportState(id));
        assertEquals(101,value("SELECT handler_id FROM report WHERE id=?",id));
        assertEquals("Checked and dismissed",jdbc.queryForObject("SELECT result FROM report WHERE id=?",String.class,id));
        assertNotNull(jdbc.queryForObject("SELECT handled_at FROM report WHERE id=?",LocalDateTime.class,id));
    }
    @Test void resolvingWithOfflineAtomicallyRemovesPublicAccessAndKeepsHistory() throws Exception {
        long paper=published(),id=report(paper),version=version(paper);
        ok(resolve(id,"Copyright confirmed",true));assertEquals("offline",state(paper));assertEquals(version+1,version(paper));
        assertEquals("moderation",jdbc.queryForObject("SELECT offline_reason FROM paper WHERE id=?",String.class,paper));
        assertEquals("resolved",reportState(id));assertEquals(1,actionCount(paper,"论文下架"));
        api("GET","/shared-papers/"+paper,null,user).andExpect(status().isNotFound());
        api("GET","/papers/"+paper+"/file",null,other).andExpect(status().isNotFound());
        api("GET","/papers/"+paper+"/file",null,user).andExpect(status().isOk());
        flow(paper,"resubmit",null,user).andExpect(status().isBadRequest());
    }
    @Test void resolvedReportsCannotBeProcessedAgain() throws Exception {
        long paper=published(),id=report(paper);ok(resolve(id,"First result",false));
        resolve(id,"Replacement",true).andExpect(status().isConflict());
        assertEquals("approved",state(paper));assertEquals("First result",jdbc.queryForObject("SELECT result FROM report WHERE id=?",String.class,id));
        assertEquals(1,value("SELECT COUNT(*) FROM operation_log WHERE action='举报处理' AND target LIKE ?","reportId="+id+";%"));
    }
    @Test void concurrentResolutionsOnlyHaveOneWinner() throws Exception {
        long paper=published(),id=report(paper);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<Integer> call=()->{start.await();return resolve(id,"Concurrent result",true).andReturn().getResponse().getStatus();};
            Future<Integer> first=pool.submit(call),second=pool.submit(call);start.countDown();
            assertEquals(new HashSet<>(Arrays.asList(200,409)),new HashSet<>(Arrays.asList(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))));
            assertEquals("resolved",reportState(id));assertEquals(1,actionCount(paper,"论文下架"));freeLock();
        } finally {pool.shutdownNow();}
    }
    @Test void twoReportsCanCloseButOnlyFirstActuallyOfflinesThePaper() throws Exception {
        long paper=published(),first=report(paper),second=report(paper);ok(resolve(first,"First",true));long version=version(paper);
        ok(resolve(second,"Second",true));assertEquals(version,version(paper));assertEquals(1,actionCount(paper,"论文下架"));
        assertEquals("resolved",reportState(first));assertEquals("resolved",reportState(second));
    }
    @Test void withdrawnAndModeratedStatesAreNotOverwrittenByReportClosure() throws Exception {
        long withdrawn=published();ok(flow(withdrawn,"unshare",null,user));
        long moderated=published();ok(api("PATCH","/admin/papers/"+moderated+"/offline",null,admin));
        for(long paper:new long[]{withdrawn,moderated}) {
            long version=version(paper);String reason=jdbc.queryForObject("SELECT offline_reason FROM paper WHERE id=?",String.class,paper);
            String comment=jdbc.queryForObject("SELECT review_comment FROM paper WHERE id=?",String.class,paper);
            ok(resolve(report(paper),"Already unavailable",true));assertEquals(version,version(paper));
            assertEquals(reason,jdbc.queryForObject("SELECT offline_reason FROM paper WHERE id=?",String.class,paper));
            assertEquals(comment,jdbc.queryForObject("SELECT review_comment FROM paper WHERE id=?",String.class,paper));
        }
        api("PATCH","/admin/papers/"+withdrawn+"/restore",null,admin).andExpect(status().isBadRequest());
    }
    @Test void pendingRejectedAndPrivateRequireNoOfflineOption() throws Exception {
        long pending=paper("shared",user),rejected=paper("shared",user),privateId=paper("private",user);
        ok(api("POST","/admin/review/papers/"+rejected+"/reject",body("comment","Not approved"),admin));
        for(long paper:new long[]{pending,rejected,privateId}) {
            long id=report(paper),version=version(paper);
            resolve(id,"Cannot offline",true).andExpect(status().isBadRequest());assertEquals("pending",reportState(id));assertEquals(version,version(paper));
            ok(resolve(id,"Close only",false));assertEquals("resolved",reportState(id));assertEquals(version,version(paper));
        }
    }
    @Test void resultAndRequestValidationCannotPartiallyResolveOrOffline() throws Exception {
        long paper=published(),id=report(paper);
        for(JsonNode body:new JsonNode[]{body(),body("result",null,"offlinePaperToo",true),body("result","x"),
                body("result","x","offlinePaperToo",null),body("result",2,"offlinePaperToo",true),
                body("result","x","offlinePaperToo","true"),body("result","x","offlinePaperToo",1),
                body("result","x","offlinePaperToo",true,"handlerId",1),body("result","x","offlinePaperToo",true,"status","resolved")})
            api("POST","/admin/reports/"+id+"/resolve",body,admin).andExpect(status().isUnprocessableEntity());
        resolve(id," \u3000\uFEFF",true).andExpect(status().isBadRequest());
        resolve(id,String.join("",Collections.nCopies(501,"x")),true).andExpect(status().isUnprocessableEntity());
        resolve(id,"value\u0000",true).andExpect(status().isUnprocessableEntity());
        assertEquals("pending",reportState(id));assertEquals("approved",state(paper));
    }
    @Test void reportResultAcceptsFiveHundredUnicodeCharacters() throws Exception {
        long paper=published(),id=report(paper);String emoji=new String(Character.toChars(0x1F600));
        String result=String.join("",Collections.nCopies(500,emoji));ok(resolve(id,"\u3000"+result+" ",false));
        assertEquals(result,jdbc.queryForObject("SELECT result FROM report WHERE id=?",String.class,id));
    }
    void failAction(String action) {
        doThrow(new DataAccessResourceFailureException("simulated moderation audit failure")).when(audit)
                .append(anyLong(),eq(action),anyString(),anyString(),any());
    }
    @Test void finalLogFailureRollsBackReportPaperAndOfflineLog() throws Exception {
        long paper=published(),id=report(paper),version=version(paper);failAction("举报处理");
        resolve(id,"Never log this content",true).andExpect(status().isInternalServerError());
        assertEquals("approved",state(paper));assertEquals(version,version(paper));assertEquals("pending",reportState(id));
        assertEquals(0,actionCount(paper,"论文下架"));assertNull(jdbc.queryForObject("SELECT handler_id FROM report WHERE id=?",Long.class,id));
        assertTrue(value("SELECT COUNT(*) FROM operation_log WHERE action='敏感数据访问' AND target LIKE ?","reportId="+id+";%")>0);freeLock();
    }
    @Test void offlineLogFailureRollsBackAllState() throws Exception {
        long paper=published(),id=report(paper);failAction("论文下架");
        resolve(id,"Rejected transaction",true).andExpect(status().isInternalServerError());
        assertEquals("approved",state(paper));assertEquals("pending",reportState(id));freeLock();
    }
    @Test void reportConditionalWriteFailureRollsBackOffline() throws Exception {
        long paper=published(),id=report(paper);doReturn(0).when(reports).resolve(eq(id),anyLong(),anyLong(),anyString(),any());
        resolve(id,"Lost update",true).andExpect(status().isConflict());assertEquals("approved",state(paper));assertEquals("pending",reportState(id));
        assertEquals(0,actionCount(paper,"论文下架"));freeLock();
    }
    @Test void reportQueryFailureKeepsIndependentAccessAudit() throws Exception {
        long before=value("SELECT COUNT(*) FROM operation_log WHERE target='collection=reports;action=reportList:pending'");
        doThrow(new DataAccessResourceFailureException("simulated report query failure")).when(reports).list("pending",false);
        api("GET","/admin/reports?status=pending",null,admin).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.data").isEmpty());
        assertEquals(before+1,value("SELECT COUNT(*) FROM operation_log WHERE target='collection=reports;action=reportList:pending'"));
    }
    @Test void sensitiveAuditFailureBlocksListsHistoryAndResolution() throws Exception {
        long paper=published(),id=report(paper);failAction("敏感数据访问");
        for(String path:new String[]{"/admin/reports","/admin/report-records","/admin/logs","/admin/settings"})
            api("GET",path,null,admin).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.data").isEmpty());
        resolve(id,"No sensitive access",true).andExpect(status().isInternalServerError());assertEquals("pending",reportState(id));assertEquals("approved",state(paper));
    }
    @Test void privateSourceAuditsFailClosedForReportSnapshotsAndResolution() throws Exception {
        long paper=paper("private",user),id=report(paper);
        assertTrue(ids(read("/admin/reports",admin)).contains(id));
        assertTrue(value("SELECT COUNT(*) FROM operation_log WHERE target=?","paperId="+paper+";ownerId=1;action=reportPrivateSnapshots")>0);
        doThrow(new DataAccessResourceFailureException("simulated private snapshot audit failure")).when(audit)
                .append(eq(101L),eq("敏感数据访问"),startsWith("paperId="+paper+";"),anyString(),any());
        api("GET","/admin/reports",null,admin).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.data").isEmpty());
        resolve(id,"No private snapshot access",false).andExpect(status().isInternalServerError());assertEquals("pending",reportState(id));
    }
    @Test void reportAndReasonContentAreNotCopiedIntoOperationLogs() throws Exception {
        long paper=published(),id=report(paper);String content="Sensitive result "+unique();ok(resolve(id,content,true));
        String reason=jdbc.queryForObject("SELECT reason FROM report WHERE id=?",String.class,id);
        assertEquals(0,value("SELECT COUNT(*) FROM operation_log WHERE target LIKE ? OR target LIKE ?","%"+content+"%","%"+reason+"%"));
    }
    @Test void lockedCurrentPaperPreservesDownloadAfterEarlierReportSnapshot() throws Exception {
        long paper=published(),id=report(paper),downloads=value("SELECT downloads FROM paper WHERE id=?",paper);
        doAnswer(invocation->{
            long source=value("SELECT paper_id FROM report WHERE id=?",id);
            api("POST","/papers/"+paper+"/download",null,other).andExpect(status().isOk());
            ReportEntity reference=new ReportEntity();reference.setId(id);reference.setPaperId(source);return reference;
        }).when(reports).reference(id);
        ok(resolve(id,"Preserve concurrent download",true));assertEquals(downloads+1,value("SELECT downloads FROM paper WHERE id=?",paper));
    }
    @Test void reportResolutionAndWithdrawalNeverRestoreOrLoseTheWithdrawalReason() throws Exception {
        long paper=published(),id=report(paper);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Future<Integer> handled=pool.submit(()->{start.await();return resolve(id,"Concurrent withdrawal",true).andReturn().getResponse().getStatus();});
            Future<Integer> withdrawn=pool.submit(()->{start.await();return flow(paper,"unshare",null,user).andReturn().getResponse().getStatus();});start.countDown();
            assertEquals(200,handled.get(10,TimeUnit.SECONDS));int withdrawal=withdrawn.get(10,TimeUnit.SECONDS);
            assertTrue(withdrawal==200||withdrawal==400||withdrawal==409);assertEquals("offline",state(paper));assertEquals("resolved",reportState(id));
            assertEquals(withdrawal==200?"withdrawn":"moderation",jdbc.queryForObject("SELECT offline_reason FROM paper WHERE id=?",String.class,paper));freeLock();
        } finally {pool.shutdownNow();}
    }
    @Test void logsUseActorNameSnapshotAndExactBoundActionFilter() throws Exception {
        String action="Custom "+unique(),snapshot="Historical user",target="resourceId="+unique();
        jdbc.update("INSERT INTO operation_log(user_id,user_name,action,target,result,time) VALUES(1,?,?,?,'失败','2025-02-03 12:00:00')",snapshot,action,target);
        JsonNode records=logQuery(action);assertEquals(1,records.size());JsonNode row=records.get(0);
        assertEquals(6,row.size());assertEquals(snapshot,row.path("userName").asText());assertEquals(target,row.path("target").asText());
        assertEquals("失败",row.path("result").asText());assertEquals("2025-02-03 12:00:00",row.path("time").asText());assertFalse(row.has("userId"));
        for(String value:new String[]{"%","_","' OR 1=1 --"}) assertTrue(logQuery(value).isEmpty());
        assertTrue(logQuery("敏感数据访问").size()>0);
    }
    JsonNode logQuery(String action) throws Exception {
        return ok(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/logs")
                .param("action",action).header("Authorization",admin)));
    }
    @Test void logsHaveStableTimestampAndIdOrderingAndOneAuditPerRead() throws Exception {
        long before=value("SELECT COUNT(*) FROM operation_log WHERE target='collection=operationLogs;action=logList'");
        JsonNode rows=read("/admin/logs",admin);assertTrue(rows.size()>0);
        for(int i=1;i<rows.size();i++) {
            JsonNode a=rows.get(i-1),b=rows.get(i);int order=a.path("time").asText().compareTo(b.path("time").asText());
            assertTrue(order>0 || order==0 && a.path("id").asLong()>b.path("id").asLong());
        }
        assertEquals(before+1,value("SELECT COUNT(*) FROM operation_log WHERE target='collection=operationLogs;action=logList'"));
    }
    @Test void settingsAreReadOnlySafeAndReflectActualPdfLimit() throws Exception {
        JsonNode settings=read("/admin/settings",admin);assertEquals(7,settings.size());
        assertEquals(LocalFileStorage.PDF_LIMIT/(1024*1024),settings.path("maxUploadMB").asLong());assertEquals(".pdf",settings.path("allowedTypes").asText());
        assertEquals("PH",settings.path("logoText").asText());assertFalse(settings.has("jwtSecret"));assertFalse(settings.has("storageRoot"));
        for(String method:new String[]{"POST","PATCH","PUT","DELETE"})
            api(method,"/admin/settings",body("maxUploadMB",200),admin).andExpect(status().isUnprocessableEntity());
        assertEquals(settings,read("/admin/settings",admin));
    }
    @Test void invalidFiltersAndIdsAreRejectedWithoutInventingNewEndpoints() throws Exception {
        api("GET","/admin/reports?status=bogus",null,admin).andExpect(status().isUnprocessableEntity());
        logQueryInvalid(String.join("",Collections.nCopies(51,"x")));
        resolve(0,"Invalid",false).andExpect(status().isUnprocessableEntity());
        resolve(9007199254740991L,"Missing",false).andExpect(status().isNotFound());
        api("POST","/reports",body("paperId",2001),user).andExpect(status().isNotFound());
    }
    void logQueryInvalid(String action) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/logs")
                .param("action",action).header("Authorization",admin)).andExpect(status().isUnprocessableEntity());
    }
    @Test void nonAdminAnonymousAndDisabledActorsCannotReadOrResolve() throws Exception {
        long id=report(published());
        for(String path:new String[]{"/admin/reports","/admin/report-records","/admin/logs","/admin/settings"}) {
            for(String actor:new String[]{user,other}) api("GET",path,null,actor).andExpect(status().isForbidden());
            api("GET",path,null,null).andExpect(status().isUnauthorized());
        }
        for(String actor:new String[]{user,other}) api("POST","/admin/reports/"+id+"/resolve",body("result","Spoof","offlinePaperToo",true),actor).andExpect(status().isForbidden());
        api("POST","/admin/reports/"+id+"/resolve",body("result","Spoof","offlinePaperToo",true),null).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE `user` SET status='disabled',token_version=token_version+1 WHERE id=101");
        api("GET","/admin/reports",null,admin).andExpect(status().isUnauthorized());resolve(id,"Disabled",true).andExpect(status().isUnauthorized());
        assertEquals("pending",reportState(id));
    }
}
