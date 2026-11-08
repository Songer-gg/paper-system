package cn.paperhub.statistics.vo;

import java.util.List;

public final class UserStatisticsView {
    public final long totalPapers,privateCount,sharedCount,favoriteCount,readCount,noteCount;
    public final List<ChartPoint.MonthCount> monthlyReadingTrend;
    public final List<ChartPoint.NameCount> yearDistribution,categoryDistribution,journalDistribution;
    public UserStatisticsView(long total,long privateCount,long sharedCount,long favorites,long read,long notes,
                              List<ChartPoint.MonthCount> months,List<ChartPoint.NameCount> years,
                              List<ChartPoint.NameCount> categories,List<ChartPoint.NameCount> journals) {
        totalPapers=total;this.privateCount=privateCount;this.sharedCount=sharedCount;
        favoriteCount=favorites;readCount=read;noteCount=notes;monthlyReadingTrend=months;
        yearDistribution=years;categoryDistribution=categories;journalDistribution=journals;
    }
}
