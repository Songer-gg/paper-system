package cn.paperhub.statistics;

import cn.paperhub.statistics.service.StatisticsCharts;
import cn.paperhub.statistics.service.StatisticsCharts.*;
import cn.paperhub.statistics.vo.ChartPoint.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatisticsChartsTest {
    @Test void windowUsesShanghaiAtUtcYearBoundaryAndFillsTwelveMonths() {
        Window window=new Window(Clock.fixed(Instant.parse("2026-12-31T16:30:00Z"),ZoneOffset.UTC));
        assertEquals(LocalDateTime.of(2027,1,1,0,0),window.dayStart);assertEquals(LocalDateTime.of(2027,1,2,0,0),window.dayEnd);
        assertEquals("2026-02",window.months.get(0));assertEquals("2027-01",window.months.get(11));
        assertEquals(LocalDateTime.of(2026,2,1,0,0),window.start);assertEquals(LocalDateTime.of(2027,2,1,0,0),window.end);
        assertEquals(12,window.values(Collections.emptyMap()).size());
        List<MonthCount> values=window.reading(Collections.singletonMap("2027-01",2L));
        assertEquals("1月",values.get(11).label);assertEquals(2,values.get(11).count);assertEquals(0,values.get(0).count);
    }
    @Test void rootGroupingHandlesMissingNodesAndCyclesWithoutLeakingUnknownNames() {
        Map<Long,CategoryNode> tree=new HashMap<>();tree.put(1L,new CategoryNode(null,"Root"));tree.put(2L,new CategoryNode(1L,"Child"));
        tree.put(3L,new CategoryNode(4L,"Hidden"));tree.put(4L,new CategoryNode(3L,"Cycle"));
        Map<Long,Long> counts=new HashMap<>();counts.put(1L,1L);counts.put(2L,2L);counts.put(null,1L);counts.put(999L,1L);counts.put(3L,1L);
        List<NameCount> roots=StatisticsCharts.roots(counts,tree);assertEquals(2,roots.size());
        assertEquals(3,roots.get(0).count);assertEquals(3,roots.get(1).count);
        assertEquals("未分类",StatisticsCharts.root(3L,tree));assertEquals("Root",StatisticsCharts.root(2L,tree));
    }
    @Test void topCategoryOverflowPreservesTotalsAndMergesAnExistingOtherLabel() {
        List<NameCount> counts=Arrays.asList(new NameCount("其他",5),new NameCount("Root",4),new NameCount("Remainder",3));
        List<NameValue> result=StatisticsCharts.values(counts,2,true);assertEquals(2,result.size());
        assertEquals("其他",result.get(0).name);assertEquals(8,result.get(0).value);assertEquals(12,result.stream().mapToLong(row->row.value).sum());
    }
}
