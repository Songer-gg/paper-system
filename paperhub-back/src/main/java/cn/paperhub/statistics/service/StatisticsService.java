package cn.paperhub.statistics.service;

import cn.paperhub.admin.service.AdminUserService;
import cn.paperhub.audit.service.AuditService;
import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.mapper.PaperMapper;
import cn.paperhub.paper.service.PaperListingService;
import cn.paperhub.paper.vo.PaperView;
import cn.paperhub.security.SessionPrincipal;
import cn.paperhub.statistics.mapper.StatisticsStore;
import cn.paperhub.statistics.mapper.StatisticsStore.*;
import cn.paperhub.statistics.vo.*;
import cn.paperhub.statistics.vo.ChartPoint.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class StatisticsService {
    private final StatisticsStore store;
    private final PaperMapper papers;
    private final PaperListingService listing;
    private final AuditService audit;
    private final Clock clock;
    public StatisticsService(StatisticsStore store,PaperMapper papers,PaperListingService listing,AuditService audit,Clock clock) {
        this.store=store;this.papers=papers;this.listing=listing;this.audit=audit;this.clock=clock;
    }
    public UserDashboardView dashboard(SessionPrincipal actor) {
        privateScope(actor,"ownDashboardAggregates");OwnedCounts own=store.ownCounts(actor.getId());
        Map<String,Long> readings=store.readingStates(actor);
        UserDashboardView.Counts counts=new UserDashboardView.Counts(own.total,own.privateCount,own.sharedCount,
                store.favoriteCount(actor.getId()),readings.getOrDefault("read",0L));
        List<YearCount> years=new ArrayList<>();for(NameCount year:store.years(actor.getId())) years.add(new YearCount(year.name,year.count));
        List<NameValue> categories=StatisticsCharts.values(StatisticsCharts.roots(store.ownCategories(actor.getId()),store.categories(actor)),6,true);
        List<ReadingCount> distribution=new ArrayList<>();String[] statuses={"read","reading","unread","paused"};
        String[] labels={"已阅读","阅读中","未阅读","暂不阅读"};
        for(int i=0;i<statuses.length;i++) distribution.add(new ReadingCount(statuses[i],labels[i],readings.getOrDefault(statuses[i],0L)));
        List<Activity> recentReadings=store.recentReadings(actor),recentFavorites=store.recentFavorites(actor.getId());
        Map<Long,PaperView> activities=activityPapers(recentReadings,recentFavorites,actor);
        List<UserDashboardView.RecentReading> readViews=new ArrayList<>();
        for(Activity row:recentReadings) readViews.add(new UserDashboardView.RecentReading(activities.get(row.paperId),row.time,row.status));
        List<UserDashboardView.RecentFavorite> favoriteViews=new ArrayList<>();
        for(Activity row:recentFavorites) favoriteViews.add(new UserDashboardView.RecentFavorite(activities.get(row.paperId),row.time));
        List<PaperView> uploads=listing.views(papers.selectList(new QueryWrapper<PaperEntity>().eq("uploader_id",actor.getId())
                .isNull("deleted_at").orderByDesc("uploaded_at","id").last("LIMIT 4")),actor,"ownDashboardUploads");
        return new UserDashboardView(counts,years,categories,distribution,uploads,readViews,favoriteViews);
    }
    public UserStatisticsView statistics(SessionPrincipal actor) {
        privateScope(actor,"ownStatisticsAggregates");OwnedCounts own=store.ownCounts(actor.getId());
        StatisticsCharts.Window window=new StatisticsCharts.Window(clock);
        return new UserStatisticsView(own.total,own.privateCount,own.sharedCount,store.favoriteCount(actor.getId()),
                store.readingStates(actor).getOrDefault("read",0L),store.noteCount(actor.getId()),window.reading(store.readingMonths(actor,window)),
                store.years(actor.getId()),StatisticsCharts.roots(store.ownCategories(actor.getId()),store.categories(actor)),store.journals(actor.getId()));
    }
    public AdminDashboardView admin(SessionPrincipal actor) {
        AdminUserService.requireAdmin(actor);audit.collection(actor,"adminDashboard","aggregateView");
        StatisticsCharts.Window window=new StatisticsCharts.Window(clock);
        return new AdminDashboardView(store.adminTotals(window),
                new AdminDashboardView.UploadTrend(window.months,window.values(store.uploadMonths(window))),
                new AdminDashboardView.RegisterTrend(window.months,window.values(store.registrationMonths(window))),
                StatisticsCharts.values(StatisticsCharts.roots(store.publicCategories(),store.categories(actor)),Integer.MAX_VALUE,false),
                listing.views(papers.selectList(new QueryWrapper<PaperEntity>().eq("visibility","shared").isNull("deleted_at")
                        .orderByDesc("uploaded_at","id").last("LIMIT 6")),actor,"adminDashboardUploads"),
                listing.views(papers.selectList(new QueryWrapper<PaperEntity>().eq("visibility","shared").eq("review_status","pending")
                        .isNull("deleted_at").orderByDesc("uploaded_at","id")),actor,"adminDashboardPending"));
    }
    private void privateScope(SessionPrincipal actor,String action) {
        if(!"admin".equals(actor.getRole())) return;
        audit.sensitiveResources(actor,"user",Collections.singletonMap(actor.getId(),actor.getId()),action,true);
        Map<Long,Long> sources=store.privateSources(actor);
        if(!sources.isEmpty()) audit.sensitiveResources(actor,"paper",sources,action,true);
    }
    private Map<Long,PaperView> activityPapers(List<Activity> readings,List<Activity> favorites,SessionPrincipal actor) {
        Set<Long> ids=new LinkedHashSet<>();for(Activity row:readings) ids.add(row.paperId);for(Activity row:favorites) ids.add(row.paperId);
        Map<Long,PaperView> result=new HashMap<>();
        if(!ids.isEmpty()) for(PaperView paper:listing.views(papers.selectList(new QueryWrapper<PaperEntity>().in("id",ids).isNull("deleted_at")),actor,"ownDashboardActivity"))
            result.put(paper.getId(),paper);
        return result;
    }
}
