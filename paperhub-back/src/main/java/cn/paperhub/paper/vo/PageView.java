package cn.paperhub.paper.vo;

import java.util.List;

public final class PageView<T> {
    public final List<T> list;
    public final long total;
    public PageView(List<T> list,long total) { this.list=list; this.total=total; }
}

