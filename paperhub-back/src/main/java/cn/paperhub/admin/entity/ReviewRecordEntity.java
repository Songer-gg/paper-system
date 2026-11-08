package cn.paperhub.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("review_record")
public class ReviewRecordEntity {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long paperId;
    private Long reviewerId;
    private String paperTitle;
    private Long uploaderId;
    private String action;
    private String comment;
    private LocalDateTime reviewedAt;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getPaperId() { return paperId; }
    public void setPaperId(Long value) { paperId=value; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long value) { reviewerId=value; }
    public String getPaperTitle() { return paperTitle; }
    public void setPaperTitle(String value) { paperTitle=value; }
    public Long getUploaderId() { return uploaderId; }
    public void setUploaderId(Long value) { uploaderId=value; }
    public String getAction() { return action; }
    public void setAction(String value) { action=value; }
    public String getComment() { return comment; }
    public void setComment(String value) { comment=value; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime value) { reviewedAt=value; }
}

