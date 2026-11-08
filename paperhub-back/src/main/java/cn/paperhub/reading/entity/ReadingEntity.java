package cn.paperhub.reading.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("reading_record")
public class ReadingEntity {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long userId;
    private Long paperId;
    private String status;
    private LocalDateTime addedAt;
    private LocalDateTime lastReadAt;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId=value; }
    public Long getPaperId() { return paperId; }
    public void setPaperId(Long value) { paperId=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime value) { addedAt=value; }
    public LocalDateTime getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(LocalDateTime value) { lastReadAt=value; }
}

