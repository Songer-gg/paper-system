package cn.paperhub.knowledge.dto;

import javax.validation.constraints.*;

public class CategoryCreateRequest {
    @NotBlank @Size(max=50)
    private String name;
    private Long parentId;
    public String getName() { return name; }
    public void setName(String name) { this.name=name; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId=parentId; }
}

