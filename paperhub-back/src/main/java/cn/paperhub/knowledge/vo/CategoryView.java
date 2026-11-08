package cn.paperhub.knowledge.vo;

import cn.paperhub.knowledge.entity.CategoryEntity;

public final class CategoryView {
    public final Long id, parentId, ownerId;
    public final String name, desc;
    public final boolean custom;
    public final long paperCount;
    public CategoryView(CategoryEntity category, long paperCount) {
        id=category.getId(); parentId=category.getParentId(); ownerId=category.getOwnerId();
        name=category.getName(); desc=category.getDescription()==null?"":category.getDescription();
        custom=Boolean.TRUE.equals(category.getCustom()); this.paperCount=paperCount;
    }
}

