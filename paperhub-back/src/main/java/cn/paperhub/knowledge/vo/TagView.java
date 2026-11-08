package cn.paperhub.knowledge.vo;

import cn.paperhub.knowledge.entity.TagEntity;

public final class TagView {
    public final Long id, ownerId;
    public final String name, color;
    public final boolean custom;
    public final long paperCount;
    public TagView(TagEntity tag, long paperCount) {
        id=tag.getId(); ownerId=tag.getOwnerId(); name=tag.getName(); color=tag.getColor();
        custom=Boolean.TRUE.equals(tag.getCustom()); this.paperCount=paperCount;
    }
}

