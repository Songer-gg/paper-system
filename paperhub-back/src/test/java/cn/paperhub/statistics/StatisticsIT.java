package cn.paperhub.statistics;

import cn.paperhub.statistics.mapper.StatisticsStore;
import cn.paperhub.support.SharingSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="paperhub.storage.cleanup-enabled=false")
@AutoConfigureMockMvc
@Import(StatisticsIT.FixedTime.class)
class StatisticsIT extends SharingSupport {
    @TestConfiguration static class FixedTime {
        @Bean @Primary Clock statisticsClock() { return Clock.fixed(Instant.parse("2026-12-31T16:30:00Z"),ZoneId.of("Asia/Shanghai")); }
    }
    @SpyBean StatisticsStore statistics;
    final List<Long> created=new ArrayList<>();
    @AfterEach void resetStatistics() {
        reset(statistics);
        for(Long id:created) jdbc.update("UPDATE `user` SET role='user' WHERE id=?",id);
        created.clear();
    }
    long actor(String role) {
        String key="statistics"+unique().replace("-","");
        jdbc.update("INSERT INTO `user`(username,password,name,email,role,status,registered_at) "+
                "SELECT ?,password,?,?,?,'active','2027-01-01 00:00:00' FROM `user` WHERE id=1",key,key,key+"@x.cn",role);
        long id=value("SELECT id FROM `user` WHERE username=?",key);created.add(id);return id;
    }
    String token(long owner) { return "Bearer "+jwt.issue(users.selectById(owner)); }
    // Metadata fixtures intentionally represent allowed legacy records without PDFs; only the isolated DB is touched.
    long resource(long owner,String visibility,String state,Long category,int year,String journal,String time) {
        String title="Statistics "+unique();
        jdbc.update("INSERT INTO paper(title,authors,abstract,year,keywords,category_id,visibility,uploader_id,uploaded_at,review_status,offline_reason,journal) "+
                "VALUES(?,JSON_ARRAY('Author'),'Fixture',?,JSON_ARRAY(),?,?,?,?,?,?,?)",title,year,category,visibility,owner,time,state,
                "offline".equals(state)?"moderation":null,journal);
        return value("SELECT id FROM paper WHERE title=?",title);
    }
    long privatePaper(long owner) { return resource(owner,"private",null,1L,2026,"Journal","2027-01-01 00:00:00"); }
    long shared(long owner,String state) { return resource(owner,"shared",state,1L,2026,"Journal","2027-01-01 00:00:00"); }
    void reading(long owner,long paper,String status,String time) {
        jdbc.update("INSERT INTO reading_record(user_id,paper_id,status,added_at,last_read_at) VALUES(?,?,?,'2020-01-01 00:00:00',?)",owner,paper,status,time);
    }
    void favorite(long owner,long paper,String time) { jdbc.update("INSERT INTO favorite(user_id,paper_id,favorited_at) VALUES(?,?,?)",owner,paper,time); }
    JsonNode dashboard(long owner) throws Exception { return read("/me/dashboard",token(owner)); }
    JsonNode stats(long owner) throws Exception { return read("/me/statistics",token(owner)); }
    long sum(JsonNode list,String field) { long sum=0;for(JsonNode row:list) sum+=row.path(field).asLong();return sum; }
    JsonNode byName(JsonNode list,String name) { for(JsonNode row:list) if(row.path("name").asText().equals(name)) return row;fail("Missing chart name");return null; }
    JsonNode byMonth(JsonNode list,String month) { for(JsonNode row:list) if(row.path("month").asText().equals(month)) return row;fail("Missing month");return null; }
    @Test void emptyAccountsHaveCompleteZeroShapesAndCannotImpersonateAnotherOwner() throws Exception {
        long owner=actor("user");JsonNode dashboard=dashboard(owner),stats=stats(owner);
        assertEquals(7,dashboard.size());assertEquals(5,dashboard.path("counts").size());assertEquals(10,stats.size());
        assertEquals(0,dashboard.path("counts").path("total").asLong());assertTrue(dashboard.path("recentUploads").isEmpty());
        assertTrue(dashboard.path("recentFavorites").isEmpty());assertTrue(dashboard.path("recentReadings").isEmpty());
        assertEquals(4,dashboard.path("readingDist").size());assertEquals(0,sum(dashboard.path("readingDist"),"count"));
        assertEquals(12,stats.path("monthlyReadingTrend").size());assertEquals(0,sum(stats.path("monthlyReadingTrend"),"count"));
        assertEquals("2026-02",stats.path("monthlyReadingTrend").get(0).path("month").asText());
        assertEquals("2027-01",stats.path("monthlyReadingTrend").get(11).path("month").asText());
        assertEquals(dashboard,read("/me/dashboard?userId=1&ownerId=101",token(owner)));
        assertEquals(stats,read("/me/statistics?userId=1",token(owner)));
    }
    @Test void ownedCountsIncludeAllSharedStatesAndExcludeOtherOwnersAndDeletedPapers() throws Exception {
        long owner=actor("user");privatePaper(owner);
        for(String state:new String[]{"approved","pending","rejected","offline"}) shared(owner,state);
        long deleted=privatePaper(owner);jdbc.update("UPDATE paper SET deleted_at='2027-01-01 00:10:00' WHERE id=?",deleted);
        privatePaper(actor("user"));JsonNode dashboard=dashboard(owner),stats=stats(owner);
        assertEquals(5,stats.path("totalPapers").asLong());assertEquals(1,stats.path("privateCount").asLong());assertEquals(4,stats.path("sharedCount").asLong());
        assertEquals(5,dashboard.path("counts").path("total").asLong());assertEquals(1,dashboard.path("counts").path("private").asLong());
        assertEquals(4,dashboard.path("counts").path("shared").asLong());assertFalse(dashboard.path("counts").has("privateCount"));
        assertEquals(5,sum(stats.path("yearDistribution"),"count"));
        assertEquals(read("/me/profile",token(owner)).path("uploadedCount"),stats.path("totalPapers"));
    }
    @Test void favoritesAndReadingsUseTheSameVisibilityAsPersonalListsAndKeepRetainedNotes() throws Exception {
        long owner=actor("user"),foreign=actor("user"),privateId=privatePaper(owner),visible=shared(foreign,"approved"),offline=shared(foreign,"offline"),hidden=privatePaper(foreign),deleted=shared(foreign,"approved");
        jdbc.update("UPDATE paper SET deleted_at='2027-01-01 00:00:00' WHERE id=?",deleted);
        for(long id:new long[]{visible,offline,hidden,deleted}) favorite(owner,id,"2027-01-01 00:00:00");
        for(long id:new long[]{privateId,visible,offline,hidden,deleted}) reading(owner,id,"read","2027-01-01 00:00:00");
        jdbc.update("INSERT INTO note(user_id,paper_id,paper_title,title,content) VALUES(?,?,'Old source','Retained','Private text')",owner,deleted);
        JsonNode stats=stats(owner),dashboard=dashboard(owner);
        assertEquals(1,stats.path("favoriteCount").asLong());assertEquals(2,stats.path("readCount").asLong());assertEquals(1,stats.path("noteCount").asLong());
        assertEquals(read("/me/favorites",token(owner)).size(),stats.path("favoriteCount").asLong());assertEquals(read("/me/reading-records",token(owner)).size(),stats.path("readCount").asLong());
        assertEquals(1,dashboard.path("counts").path("favorites").asLong());assertEquals(2,dashboard.path("counts").path("read").asLong());
        assertEquals(2,sum(stats.path("monthlyReadingTrend"),"count"));assertEquals(2,dashboard.path("recentReadings").size());
        assertEquals(1,dashboard.path("recentFavorites").size());
    }
    @Test void readingDistributionHasStableOrderLabelsAndIncludesAllFourStates() throws Exception {
        long owner=actor("user");String[] states={"read","reading","unread","paused"},labels={"已阅读","阅读中","未阅读","暂不阅读"};
        for(String state:states) reading(owner,privatePaper(owner),state,"2027-01-01 00:00:00");
        JsonNode dashboard=dashboard(owner);assertEquals(1,dashboard.path("counts").path("read").asLong());
        for(int i=0;i<4;i++) {JsonNode point=dashboard.path("readingDist").get(i);assertEquals(states[i],point.path("status").asText());assertEquals(labels[i],point.path("label").asText());assertEquals(1,point.path("count").asLong());}
        assertEquals(4,sum(stats(owner).path("monthlyReadingTrend"),"count"));
    }
    @Test void readingMonthsUseLastReadAtAndExactShanghaiWindowBoundaries() throws Exception {
        long owner=actor("user");String[] times={"2026-01-31 23:59:59","2026-02-01 00:00:00","2026-12-31 23:59:59","2027-01-01 00:00:00","2027-01-31 23:59:59","2027-02-01 00:00:00"};
        for(String time:times) reading(owner,privatePaper(owner),"paused",time);
        JsonNode months=stats(owner).path("monthlyReadingTrend");assertEquals(12,months.size());assertEquals(4,sum(months,"count"));
        assertEquals(1,byMonth(months,"2026-02").path("count").asLong());assertEquals(1,byMonth(months,"2026-12").path("count").asLong());
        assertEquals(2,byMonth(months,"2027-01").path("count").asLong());assertEquals(0,byMonth(months,"2026-03").path("count").asLong());
    }
    @Test void recentListsLimitAfterVisibilityFilteringAndUseDeterministicTieOrdering() throws Exception {
        long owner=actor("user"),foreign=actor("user");List<Long> ids=new ArrayList<>();
        for(int i=0;i<7;i++) { long id=shared(owner,"approved");ids.add(id);reading(owner,id,"reading","2027-01-01 00:00:00");favorite(owner,id,"2027-01-01 00:00:00"); }
        long hidden=shared(foreign,"offline");reading(owner,hidden,"read","2027-12-01 00:00:00");favorite(owner,hidden,"2027-12-01 00:00:00");
        JsonNode dashboard=dashboard(owner);
        for(String name:new String[]{"recentUploads","recentReadings","recentFavorites"}) {
            JsonNode rows=dashboard.path(name);assertEquals(4,rows.size());
            for(int i=0;i<4;i++) {JsonNode paper="recentUploads".equals(name)?rows.get(i):rows.get(i).path("paper");assertEquals(ids.get(6-i).longValue(),paper.path("id").asLong());assertFalse(paper.has("filePath"));assertFalse(paper.has("fileSha256"));}
        }
        assertEquals(3,dashboard.path("recentReadings").get(0).size());assertEquals(2,dashboard.path("recentFavorites").get(0).size());
    }
    long category(long owner,String name,Long parent) {
        jdbc.update("INSERT INTO category(name,parent_id,custom,owner_id) VALUES(?,?,1,?)",name,parent,owner);
        return value("SELECT id FROM category WHERE owner_id=? AND name=?",owner,name);
    }
    @Test void categoriesRollUpToRootAndDashboardGroupsOnlyOverflowAsOther() throws Exception {
        long owner=actor("user");List<Long> roots=new ArrayList<>();String prefix=unique();
        for(int i=0;i<8;i++) {long root=category(owner,"Root"+i+prefix,null);roots.add(root);resource(owner,"private",null,root,2026,"J","2027-01-01 00:00:00");}
        long child=category(owner,"Child"+prefix,roots.get(0));resource(owner,"private",null,child,2026,"J","2027-01-01 00:00:00");
        resource(owner,"private",null,null,2026,"J","2027-01-01 00:00:00");
        JsonNode dashboard=dashboard(owner),stats=stats(owner);assertEquals(7,dashboard.path("categoryDist").size());assertEquals(10,sum(dashboard.path("categoryDist"),"value"));
        assertEquals(9,stats.path("categoryDistribution").size());assertEquals(2,byName(stats.path("categoryDistribution"),"Root0"+prefix).path("count").asLong());
        assertEquals(1,byName(stats.path("categoryDistribution"),"未分类").path("count").asLong());assertEquals(3,byName(dashboard.path("categoryDist"),"其他").path("value").asLong());
    }
    @Test void yearAndJournalDistributionsUseOwnMetadataAndTopEightJournalShape() throws Exception {
        long owner=actor("user");for(int i=0;i<9;i++) resource(owner,"private",null,1L,2020+i,"Journal"+i,"2027-01-01 00:00:00");
        resource(owner,"private",null,1L,2020,null,"2027-01-01 00:00:00");resource(owner,"private",null,1L,2020,"","2027-01-01 00:00:00");
        JsonNode stats=stats(owner);assertEquals(11,stats.path("totalPapers").asLong());assertEquals(8,stats.path("journalDistribution").size());
        assertEquals(2,byName(stats.path("journalDistribution"),"未投稿").path("count").asLong());
        assertEquals("2020",stats.path("yearDistribution").get(0).path("name").asText());assertEquals(3,stats.path("yearDistribution").get(0).path("count").asLong());
        assertEquals("2028",stats.path("yearDistribution").get(8).path("name").asText());
    }
    @Test void adminDashboardCountsOnlyNormalAccountsAndPublicSharedCards() throws Exception {
        long owner=actor("user");long disabled=actor("user");jdbc.update("UPDATE `user` SET status='disabled' WHERE id=?",disabled);actor("admin");
        shared(owner,"approved");shared(owner,"pending");shared(owner,"rejected");shared(owner,"offline");privatePaper(owner);
        long deleted=shared(owner,"approved");jdbc.update("UPDATE paper SET deleted_at='2027-01-01 00:00:00' WHERE id=?",deleted);
        JsonNode admin=read("/admin/dashboard",this.admin);assertEquals(6,admin.size());JsonNode totals=admin.path("totals");assertEquals(5,totals.size());
        assertEquals(value("SELECT COUNT(*) FROM `user` WHERE role='user'"),totals.path("users").asLong());
        assertEquals(value("SELECT COUNT(*) FROM paper WHERE visibility='shared' AND review_status='approved' AND deleted_at IS NULL"),totals.path("papers").asLong());
        assertEquals(value("SELECT COUNT(*) FROM paper WHERE visibility='shared' AND review_status='pending' AND deleted_at IS NULL"),totals.path("pending").asLong());
        assertEquals(totals.path("papers").asLong(),sum(admin.path("categoryDist"),"value"));
    }
    @Test void adminTodayAndMonthlyTrendsUseExplicitDateBoundariesAndExcludePrivateDeletedAndAdmins() throws Exception {
        long owner=actor("user"),start=shared(owner,"pending"),end=shared(owner,"rejected"),before=shared(owner,"offline"),after=shared(owner,"approved");
        jdbc.update("UPDATE paper SET uploaded_at='2027-01-01 00:00:00' WHERE id=?",start);
        jdbc.update("UPDATE paper SET uploaded_at='2027-01-01 23:59:59' WHERE id=?",end);
        jdbc.update("UPDATE paper SET uploaded_at='2026-12-31 23:59:59' WHERE id=?",before);
        jdbc.update("UPDATE paper SET uploaded_at='2027-01-02 00:00:00' WHERE id=?",after);
        privatePaper(owner);long deleted=shared(owner,"pending");jdbc.update("UPDATE paper SET deleted_at='2027-01-01 00:00:00' WHERE id=?",deleted);
        actor("admin");JsonNode dashboard=read("/admin/dashboard",admin);
        assertEquals(value("SELECT COUNT(*) FROM paper WHERE visibility='shared' AND deleted_at IS NULL AND uploaded_at>='2027-01-01 00:00:00' AND uploaded_at<'2027-01-02 00:00:00'"),dashboard.path("totals").path("papersToday").asLong());
        assertEquals(value("SELECT COUNT(*) FROM `user` WHERE role='user' AND registered_at>='2027-01-01 00:00:00' AND registered_at<'2027-01-02 00:00:00'"),dashboard.path("totals").path("usersToday").asLong());
        JsonNode uploads=dashboard.path("uploadTrend"),registrations=dashboard.path("registerTrend");
        assertEquals(2,uploads.size());assertEquals(2,registrations.size());assertEquals(12,uploads.path("months").size());
        assertEquals(uploads.path("months"),registrations.path("months"));assertEquals(12,uploads.path("papers").size());assertEquals(12,registrations.path("users").size());
        for(int i=0;i<12;i++) {
            String month=uploads.path("months").get(i).asText();
            assertEquals(value("SELECT COUNT(*) FROM paper WHERE visibility='shared' AND deleted_at IS NULL AND DATE_FORMAT(uploaded_at,'%Y-%m')=?",month),uploads.path("papers").get(i).asLong());
            assertEquals(value("SELECT COUNT(*) FROM `user` WHERE role='user' AND DATE_FORMAT(registered_at,'%Y-%m')=?",month),registrations.path("users").get(i).asLong());
        }
    }
    @Test void adminRecentUploadsIncludeAllSharedStatesAndPendingTasksOnlyPending() throws Exception {
        long owner=actor("user");List<Long> newest=new ArrayList<>();
        for(String state:new String[]{"approved","pending","rejected","offline","approved","pending","rejected"}) {
            long id=shared(owner,state);jdbc.update("UPDATE paper SET uploaded_at='2030-01-01 00:00:00' WHERE id=?",id);newest.add(id);
        }
        long privateId=privatePaper(owner);jdbc.update("UPDATE paper SET uploaded_at='2031-01-01 00:00:00' WHERE id=?",privateId);
        JsonNode dashboard=read("/admin/dashboard",admin);assertEquals(6,dashboard.path("recentUploads").size());
        for(int i=0;i<6;i++) assertEquals(newest.get(6-i).longValue(),dashboard.path("recentUploads").get(i).path("id").asLong());
        assertEquals(value("SELECT COUNT(*) FROM paper WHERE visibility='shared' AND review_status='pending' AND deleted_at IS NULL"),dashboard.path("pendingTasks").size());
        for(JsonNode paper:dashboard.path("pendingTasks")) {assertEquals("pending",paper.path("reviewStatus").asText());assertEquals("shared",paper.path("visibility").asText());}
    }
    @Test void adminOwnStatisticsDoNotSwitchOwnerButAuditPrivateReadingSources() throws Exception {
        long owner=actor("admin"),foreign=actor("user"),source=privatePaper(foreign);String token=token(owner);reading(owner,source,"read","2027-01-01 00:00:00");
        assertEquals(0,stats(owner).path("totalPapers").asLong());assertEquals(1,stats(owner).path("readCount").asLong());
        JsonNode dashboard=read("/me/dashboard?userId="+foreign,token);assertEquals(0,dashboard.path("counts").path("total").asLong());
        assertEquals(source,dashboard.path("recentReadings").get(0).path("paper").path("id").asLong());
        assertTrue(value("SELECT COUNT(*) FROM operation_log WHERE user_id=? AND target LIKE ?",owner,"paperId="+source+";ownerId="+foreign+";%")>0);
    }
    @Test void privateAggregateAuditFailureBlocksCountsEvenWithoutRecentMetadata() throws Exception {
        long owner=actor("admin"),source=privatePaper(owner);String token=token(owner);
        doThrow(new DataAccessResourceFailureException("simulated aggregate private audit failure")).when(audit)
                .append(eq(owner),eq("敏感数据访问"),startsWith("paperId="+source+";"),anyString(),any());
        for(String path:new String[]{"/me/statistics","/me/dashboard"}) api("GET",path,null,token).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.data").isEmpty());
    }
    @Test void adminAggregateScopeAuditFailsClosedAndSurvivesLaterQueryFailure() throws Exception {
        long before=value("SELECT COUNT(*) FROM operation_log WHERE target='collection=adminDashboard;action=aggregateView'");
        doThrow(new DataAccessResourceFailureException("simulated aggregate query failure")).when(statistics).adminTotals(any());
        api("GET","/admin/dashboard",null,admin).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.data").isEmpty());
        assertEquals(before+1,value("SELECT COUNT(*) FROM operation_log WHERE target='collection=adminDashboard;action=aggregateView'"));reset(statistics);
        doThrow(new DataAccessResourceFailureException("simulated aggregate scope audit failure")).when(audit).append(eq(101L),eq("敏感数据访问"),anyString(),anyString(),any());
        api("GET","/admin/dashboard",null,admin).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.data").isEmpty());
    }
    @Test void countsAndRecentRowsShareOneSnapshotDuringConcurrentDeletion() throws Exception {
        long owner=actor("user");String token=token(owner);long source=paper("private",token);AtomicBoolean once=new AtomicBoolean();
        java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            doAnswer(invocation->{Object result=invocation.callRealMethod();
                if(once.compareAndSet(false,true)) pool.submit(()->ok(api("DELETE","/papers/"+source,null,token))).get(10,java.util.concurrent.TimeUnit.SECONDS);
                return result;}).when(statistics).ownCounts(owner);
            JsonNode first=dashboard(owner);assertEquals(1,first.path("counts").path("total").asLong());assertEquals(1,first.path("recentUploads").size());
            assertEquals(1,sum(first.path("yearDist"),"count"));assertEquals(0,dashboard(owner).path("counts").path("total").asLong());
        } finally {pool.shutdownNow();}
    }
    @Test void userScopedQueriesAreBoundAndDoNotExposeUnrelatedPrivateTitlesOrNames() throws Exception {
        long owner=actor("user"),foreign=actor("user");long secret=privatePaper(foreign);String title=jdbc.queryForObject("SELECT title FROM paper WHERE id=?",String.class,secret);
        for(String path:new String[]{"/me/dashboard?userId=1%20OR%201=1","/me/statistics?ownerId="+foreign}) {
            JsonNode result=read(path,token(owner));assertFalse(result.toString().contains(title));
        }
    }
    @Test void normalAnonymousDisabledAndRevokedSessionsHaveCorrectPermissions() throws Exception {
        for(String path:new String[]{"/me/dashboard","/me/statistics","/admin/dashboard"}) api("GET",path,null,null).andExpect(status().isUnauthorized());
        for(String token:new String[]{user,other}) api("GET","/admin/dashboard",null,token).andExpect(status().isForbidden());
        long owner=actor("user");String old=token(owner);jdbc.update("UPDATE `user` SET token_version=token_version+1 WHERE id=?",owner);
        for(String path:new String[]{"/me/dashboard","/me/statistics"}) api("GET",path,null,old).andExpect(status().isUnauthorized());
        long adminId=actor("admin");String disabled=token(adminId);jdbc.update("UPDATE `user` SET status='disabled' WHERE id=?",adminId);
        api("GET","/admin/dashboard",null,disabled).andExpect(status().isUnauthorized());
    }
    @Test void recentActivityTimesDoNotShiftWhenJvmDefaultZoneIsUtc() throws Exception {
        long owner=actor("user"),source=shared(owner,"approved");String expected="2027-01-01 08:45:12";
        reading(owner,source,"read",expected);favorite(owner,source,expected);
        TimeZone original=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));JsonNode dashboard=dashboard(owner);
            assertEquals(expected,dashboard.path("recentReadings").get(0).path("lastReadAt").asText());
            assertEquals(expected,dashboard.path("recentFavorites").get(0).path("favoritedAt").asText());
        } finally {TimeZone.setDefault(original);}
    }
}
