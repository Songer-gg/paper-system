package cn.paperhub.statistics.vo;

import cn.paperhub.paper.vo.PaperView;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public final class UserDashboardView {
    public final Counts counts;
    public final List<ChartPoint.YearCount> yearDist;
    public final List<ChartPoint.NameValue> categoryDist;
    public final List<ChartPoint.ReadingCount> readingDist;
    public final List<PaperView> recentUploads;
    public final List<RecentReading> recentReadings;
    public final List<RecentFavorite> recentFavorites;
    public UserDashboardView(Counts counts,List<ChartPoint.YearCount> years,List<ChartPoint.NameValue> categories,
                             List<ChartPoint.ReadingCount> readings,List<PaperView> uploads,
                             List<RecentReading> recentReadings,List<RecentFavorite> recentFavorites) {
        this.counts=counts;yearDist=years;categoryDist=categories;readingDist=readings;
        recentUploads=uploads;this.recentReadings=recentReadings;this.recentFavorites=recentFavorites;
    }
    public static final class Counts {
        public final long total,favorites,read;
        @JsonProperty("private") public final long privateCount;
        @JsonProperty("shared") public final long sharedCount;
        public Counts(long total,long privateCount,long sharedCount,long favorites,long read) {
            this.total=total;this.privateCount=privateCount;this.sharedCount=sharedCount;this.favorites=favorites;this.read=read;
        }
    }
    public static final class RecentReading {
        public final PaperView paper;
        public final LocalDateTime lastReadAt;
        public final String status;
        public RecentReading(PaperView paper,LocalDateTime time,String status) { this.paper=paper;lastReadAt=time;this.status=status; }
    }
    public static final class RecentFavorite {
        public final PaperView paper;
        public final LocalDateTime favoritedAt;
        public RecentFavorite(PaperView paper,LocalDateTime time) { this.paper=paper;favoritedAt=time; }
    }
}
