package cn.paperhub.reading.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("favorite")
public class FavoriteEntity {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long userId;
    private Long paperId;
    private LocalDateTime favoritedAt;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId=value; }
    public Long getPaperId() { return paperId; }
    public void setPaperId(Long value) { paperId=value; }
    public LocalDateTime getFavoritedAt() { return favoritedAt; }
    public void setFavoritedAt(LocalDateTime value) { favoritedAt=value; }
}

