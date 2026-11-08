package cn.paperhub.statistics.mapper;

import cn.paperhub.paper.service.PaperVisibility;
import cn.paperhub.security.SessionPrincipal;
import cn.paperhub.statistics.service.StatisticsCharts;
import cn.paperhub.statistics.vo.ChartPoint.NameCount;
import cn.paperhub.statistics.vo.AdminDashboardView.Totals;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StatisticsStore {
    private static final String FAVORITES=" FROM favorite f JOIN paper p ON p.id=f.paper_id WHERE f.user_id=? "+
            "AND p.deleted_at IS NULL AND p.visibility='shared' AND p.review_status='approved'";
    private static final String READING=" FROM reading_record r JOIN paper p ON p.id=r.paper_id WHERE r.user_id=? AND ";
    private final JdbcTemplate jdbc;
    public StatisticsStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public static final class OwnedCounts {
        public final long total,privateCount,sharedCount;
        public OwnedCounts(long total,long privateCount,long sharedCount) { this.total=total;this.privateCount=privateCount;this.sharedCount=sharedCount; }
    }
    public static final class Activity {
        public final long paperId;
        public final LocalDateTime time;
        public final String status;
        public Activity(long paperId,LocalDateTime time,String status) { this.paperId=paperId;this.time=time;this.status=status; }
    }
    public OwnedCounts ownCounts(long owner) {
        return jdbc.queryForObject("SELECT COUNT(*),COALESCE(SUM(visibility='private'),0),COALESCE(SUM(visibility='shared'),0) "+
                "FROM paper WHERE uploader_id=? AND deleted_at IS NULL",(rs,n)->new OwnedCounts(rs.getLong(1),rs.getLong(2),rs.getLong(3)),owner);
    }
    public long favoriteCount(long owner) { return jdbc.queryForObject("SELECT COUNT(*)"+FAVORITES,Long.class,owner); }
    public long noteCount(long owner) { return jdbc.queryForObject("SELECT COUNT(*) FROM note WHERE user_id=?",Long.class,owner); }
    public Map<String,Long> readingStates(SessionPrincipal actor) {
        return stringCounts("SELECT r.status,COUNT(*)"+READING+PaperVisibility.sql(actor)+" GROUP BY r.status",readingParameters(actor));
    }
    public List<NameCount> years(long owner) {
        return jdbc.query("SELECT year,COUNT(*) FROM paper WHERE uploader_id=? AND deleted_at IS NULL GROUP BY year ORDER BY year",
                (rs,n)->new NameCount(rs.getString(1),rs.getLong(2)),owner);
    }
    public List<NameCount> journals(long owner) {
        return jdbc.query("SELECT COALESCE(NULLIF(TRIM(journal),''),'未投稿') AS journal_name,COUNT(*) AS amount "+
                "FROM paper WHERE uploader_id=? AND deleted_at IS NULL GROUP BY journal_name ORDER BY amount DESC,journal_name LIMIT 8",
                (rs,n)->new NameCount(rs.getString(1),rs.getLong(2)),owner);
    }
    public Map<Long,Long> ownCategories(long owner) {
        return categoryCounts("SELECT category_id,COUNT(*) FROM paper WHERE uploader_id=? AND deleted_at IS NULL GROUP BY category_id",owner);
    }
    public Map<Long,Long> publicCategories() {
        return categoryCounts("SELECT category_id,COUNT(*) FROM paper WHERE visibility='shared' AND review_status='approved' "+
                "AND deleted_at IS NULL GROUP BY category_id");
    }
    public Map<Long,StatisticsCharts.CategoryNode> categories(SessionPrincipal actor) {
        String scope="admin".equals(actor.getRole())?"":" WHERE owner_id IS NULL OR owner_id=?";
        Object[] params="admin".equals(actor.getRole())?new Object[0]:new Object[]{actor.getId()};
        Map<Long,StatisticsCharts.CategoryNode> result=new HashMap<>();
        jdbc.query("SELECT id,parent_id,name FROM category"+scope,rs->{
            Object parent=rs.getObject(2);
            result.put(rs.getLong(1),new StatisticsCharts.CategoryNode(parent==null?null:((Number)parent).longValue(),rs.getString(3)));
        },params);return result;
    }
    public Map<String,Long> readingMonths(SessionPrincipal actor,StatisticsCharts.Window window) {
        List<Object> params=new ArrayList<>(Arrays.asList(readingParameters(actor)));params.add(window.start);params.add(window.end);
        return stringCounts("SELECT DATE_FORMAT(r.last_read_at,'%Y-%m'),COUNT(*)"+READING+PaperVisibility.sql(actor)+
                " AND r.last_read_at>=? AND r.last_read_at<? GROUP BY DATE_FORMAT(r.last_read_at,'%Y-%m')",params.toArray());
    }
    public List<Activity> recentReadings(SessionPrincipal actor) {
        return jdbc.query("SELECT r.paper_id,r.last_read_at,r.status"+READING+PaperVisibility.sql(actor)+
                " ORDER BY r.last_read_at DESC,r.id DESC LIMIT 4",
                (rs,n)->new Activity(rs.getLong(1),rs.getObject(2,LocalDateTime.class),rs.getString(3)),readingParameters(actor));
    }
    public List<Activity> recentFavorites(long owner) {
        return jdbc.query("SELECT f.paper_id,f.favorited_at"+FAVORITES+" ORDER BY f.favorited_at DESC,f.id DESC LIMIT 4",
                (rs,n)->new Activity(rs.getLong(1),rs.getObject(2,LocalDateTime.class),null),owner);
    }
    public Map<Long,Long> privateSources(SessionPrincipal actor) {
        Map<Long,Long> result=new LinkedHashMap<>();
        jdbc.query("SELECT p.id,p.uploader_id FROM paper p WHERE p.visibility='private' AND p.deleted_at IS NULL "+
                "AND (p.uploader_id=? OR EXISTS (SELECT 1 FROM reading_record r WHERE r.paper_id=p.id AND r.user_id=?)) ORDER BY p.id",
                rs->{result.put(rs.getLong(1),rs.getLong(2));},actor.getId(),actor.getId());return result;
    }
    public Totals adminTotals(StatisticsCharts.Window window) {
        return new Totals(count("SELECT COUNT(*) FROM `user` WHERE role='user'"),
                count("SELECT COUNT(*) FROM paper WHERE visibility='shared' AND review_status='approved' AND deleted_at IS NULL"),
                count("SELECT COUNT(*) FROM paper WHERE visibility='shared' AND review_status='pending' AND deleted_at IS NULL"),
                count("SELECT COUNT(*) FROM paper WHERE visibility='shared' AND deleted_at IS NULL AND uploaded_at>=? AND uploaded_at<?",window.dayStart,window.dayEnd),
                count("SELECT COUNT(*) FROM `user` WHERE role='user' AND registered_at>=? AND registered_at<?",window.dayStart,window.dayEnd));
    }
    public Map<String,Long> uploadMonths(StatisticsCharts.Window window) {
        return stringCounts("SELECT DATE_FORMAT(uploaded_at,'%Y-%m'),COUNT(*) FROM paper WHERE visibility='shared' AND deleted_at IS NULL "+
                "AND uploaded_at>=? AND uploaded_at<? GROUP BY DATE_FORMAT(uploaded_at,'%Y-%m')",window.start,window.end);
    }
    public Map<String,Long> registrationMonths(StatisticsCharts.Window window) {
        return stringCounts("SELECT DATE_FORMAT(registered_at,'%Y-%m'),COUNT(*) FROM `user` WHERE role='user' AND registered_at>=? "+
                "AND registered_at<? GROUP BY DATE_FORMAT(registered_at,'%Y-%m')",window.start,window.end);
    }
    private Map<String,Long> stringCounts(String sql,Object...params) {
        Map<String,Long> result=new LinkedHashMap<>();jdbc.query(sql,rs->{result.put(rs.getString(1),rs.getLong(2));},params);return result;
    }
    private Map<Long,Long> categoryCounts(String sql,Object...params) {
        Map<Long,Long> result=new LinkedHashMap<>();jdbc.query(sql,rs->{
            Object id=rs.getObject(1);result.put(id==null?null:((Number)id).longValue(),rs.getLong(2));
        },params);return result;
    }
    private long count(String sql,Object...params) { return jdbc.queryForObject(sql,Long.class,params); }
    private Object[] readingParameters(SessionPrincipal actor) {
        List<Object> params=new ArrayList<>();params.add(actor.getId());params.addAll(Arrays.asList(PaperVisibility.parameters(actor)));return params.toArray();
    }
}
