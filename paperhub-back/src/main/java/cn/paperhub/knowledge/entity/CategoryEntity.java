package cn.paperhub.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("category")
public class CategoryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long parentId;
    private String description;
    private Boolean custom;
    private Long ownerId;
    private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public String getName() { return name; }
    public void setName(String value) { this.name=value; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long value) { this.parentId=value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description=value; }
    public Boolean getCustom() { return custom; }
    public void setCustom(Boolean value) { this.custom=value; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long value) { this.ownerId=value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt=value; }
}

