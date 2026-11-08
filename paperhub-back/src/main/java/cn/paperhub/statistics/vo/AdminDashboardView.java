package cn.paperhub.statistics.vo;

import cn.paperhub.paper.vo.PaperView;
import java.util.List;

public final class AdminDashboardView {
    public final Totals totals;
    public final UploadTrend uploadTrend;
    public final RegisterTrend registerTrend;
    public final List<ChartPoint.NameValue> categoryDist;
    public final List<PaperView> recentUploads,pendingTasks;
    public AdminDashboardView(Totals totals,UploadTrend uploads,RegisterTrend registrations,List<ChartPoint.NameValue> categories,
                              List<PaperView> recent,List<PaperView> pending) {
        this.totals=totals;uploadTrend=uploads;registerTrend=registrations;categoryDist=categories;recentUploads=recent;pendingTasks=pending;
    }
    public static final class Totals {
        public final long users,papers,pending,papersToday,usersToday;
        public Totals(long users,long papers,long pending,long papersToday,long usersToday) {
            this.users=users;this.papers=papers;this.pending=pending;this.papersToday=papersToday;this.usersToday=usersToday;
        }
    }
    public static final class UploadTrend {
        public final List<String> months;
        public final List<Long> papers;
        public UploadTrend(List<String> months,List<Long> papers) { this.months=months;this.papers=papers; }
    }
    public static final class RegisterTrend {
        public final List<String> months;
        public final List<Long> users;
        public RegisterTrend(List<String> months,List<Long> users) { this.months=months;this.users=users; }
    }
}
