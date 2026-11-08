package cn.paperhub.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("tag")
public class TagEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String color;
    private Boolean custom;
    private Long ownerId;
    private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public String getName() { return name; }
    public void setName(String value) { this.name=value; }
    public String getColor() { return color; }
    public void setColor(String value) { this.color=value; }
    public Boolean getCustom() { return custom; }
    public void setCustom(Boolean value) { this.custom=value; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long value) { this.ownerId=value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt=value; }
}

