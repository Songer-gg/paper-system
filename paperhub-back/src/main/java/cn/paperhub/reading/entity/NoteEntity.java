package cn.paperhub.reading.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("note")
public class NoteEntity {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long userId;
    private Long paperId;
    private String title;
    private String paperTitle;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableField(exist=false)
    private Boolean paperAvailable;
    @TableField(exist=false)
    private Long sourceOwnerId;
    @TableField(exist=false)
    private String sourceVisibility;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId=value; }
    public Long getPaperId() { return paperId; }
    public void setPaperId(Long value) { paperId=value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title=value; }
    public String getPaperTitle() { return paperTitle; }
    public void setPaperTitle(String value) { paperTitle=value; }
    public String getContent() { return content; }
    public void setContent(String value) { content=value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt=value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt=value; }
    public Boolean getPaperAvailable() { return paperAvailable; }
    public void setPaperAvailable(Boolean value) { paperAvailable=value; }
    public Long getSourceOwnerId() { return sourceOwnerId; }
    public void setSourceOwnerId(Long value) { sourceOwnerId=value; }
    public String getSourceVisibility() { return sourceVisibility; }
    public void setSourceVisibility(String value) { sourceVisibility=value; }
}

