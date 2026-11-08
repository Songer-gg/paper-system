package cn.paperhub.statistics.service;

import cn.paperhub.statistics.vo.ChartPoint.*;
import java.time.*;
import java.util.*;

public final class StatisticsCharts {
    private StatisticsCharts() { }
    public static final class Window {
        public final LocalDateTime dayStart,dayEnd,start,end;
        public final List<String> months=new ArrayList<>();
        public Window(Clock clock) {
            LocalDate today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
            dayStart=today.atStartOfDay();dayEnd=today.plusDays(1).atStartOfDay();
            YearMonth current=YearMonth.from(today);
            start=current.minusMonths(11).atDay(1).atStartOfDay();end=current.plusMonths(1).atDay(1).atStartOfDay();
            for(int i=11;i>=0;i--) months.add(current.minusMonths(i).toString());
        }
        public List<Long> values(Map<String,Long> counts) {
            List<Long> result=new ArrayList<>();for(String month:months) result.add(counts.getOrDefault(month,0L));return result;
        }
        public List<MonthCount> reading(Map<String,Long> counts) {
            List<MonthCount> result=new ArrayList<>();
            for(String month:months) result.add(new MonthCount(month,YearMonth.parse(month).getMonthValue()+"月",counts.getOrDefault(month,0L)));
            return result;
        }
    }
    public static final class CategoryNode {
        public final Long parentId;
        public final String name;
        public CategoryNode(Long parentId,String name) { this.parentId=parentId;this.name=name; }
    }
    public static String root(Long id,Map<Long,CategoryNode> categories) {
        Set<Long> visited=new HashSet<>();
        while(id!=null && visited.add(id)) {
            CategoryNode current=categories.get(id);
            if(current==null) return "未分类";
            if(current.parentId==null) return current.name;id=current.parentId;
        }
        return "未分类";
    }
    public static List<NameCount> roots(Map<Long,Long> counts,Map<Long,CategoryNode> categories) {
        Map<String,Long> totals=new HashMap<>();
        for(Map.Entry<Long,Long> entry:counts.entrySet()) totals.merge(root(entry.getKey(),categories),entry.getValue(),Long::sum);
        List<NameCount> result=new ArrayList<>();for(Map.Entry<String,Long> entry:totals.entrySet()) result.add(new NameCount(entry.getKey(),entry.getValue()));
        result.sort(Comparator.comparingLong((NameCount point)->point.count).reversed().thenComparing(point->point.name));return result;
    }
    public static List<NameValue> values(List<NameCount> counts,int limit,boolean groupRest) {
        List<NameValue> result=new ArrayList<>();long rest=0;
        for(int i=0;i<counts.size();i++) {
            NameCount point=counts.get(i);if(i<limit) result.add(new NameValue(point.name,point.count));else rest+=point.count;
        }
        if(groupRest && rest>0) {
            for(int i=0;i<result.size();i++) if("其他".equals(result.get(i).name)) {
                result.set(i,new NameValue("其他",result.get(i).value+rest));return result;
            }
            result.add(new NameValue("其他",rest));
        }
        return result;
    }
}
