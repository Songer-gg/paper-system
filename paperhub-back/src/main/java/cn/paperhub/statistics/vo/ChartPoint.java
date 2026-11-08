package cn.paperhub.statistics.vo;

public final class ChartPoint {
    private ChartPoint() { }
    public static final class NameCount {
        public final String name;
        public final long count;
        public NameCount(String name,long count) { this.name=name;this.count=count; }
    }
    public static final class NameValue {
        public final String name;
        public final long value;
        public NameValue(String name,long value) { this.name=name;this.value=value; }
    }
    public static final class YearCount {
        public final String year;
        public final long count;
        public YearCount(String year,long count) { this.year=year;this.count=count; }
    }
    public static final class MonthCount {
        public final String month,label;
        public final long count;
        public MonthCount(String month,String label,long count) { this.month=month;this.label=label;this.count=count; }
    }
    public static final class ReadingCount {
        public final String status,label;
        public final long count;
        public ReadingCount(String status,String label,long count) { this.status=status;this.label=label;this.count=count; }
    }
}
