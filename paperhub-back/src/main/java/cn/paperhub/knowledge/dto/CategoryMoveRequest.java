package cn.paperhub.knowledge.dto;

import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonSetter;

public class CategoryMoveRequest {
    private Long parentId;
    private boolean supplied;
    public Long getParentId() { return parentId; }
    @JsonSetter("parentId")
    public void setParentId(Long parentId) { this.parentId=parentId; supplied=true; }
    public void requireParent() {
        if (!supplied) throw new BusinessException(ErrorCode.INVALID_PARAM, "必须指定 parentId，根分类使用 null");
    }
}

