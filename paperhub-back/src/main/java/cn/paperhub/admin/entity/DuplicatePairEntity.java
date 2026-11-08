package cn.paperhub.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("duplicate_pair")
public class DuplicatePairEntity {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long originalPaperId;
    private Long newPaperId;
    private String originalPaperTitle;
    private String newPaperTitle;
    private Boolean doiMatch;
    private Boolean fileHashMatch;
    private LocalDateTime submittedAt;
    private BigDecimal titleSimilarity;
    private String reason;
    private String status;
    private Long handlerId;
    private String handlerNote;
    private LocalDateTime handledAt;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getOriginalPaperId() { return originalPaperId; }
    public void setOriginalPaperId(Long value) { originalPaperId=value; }
    public Long getNewPaperId() { return newPaperId; }
    public void setNewPaperId(Long value) { newPaperId=value; }
    public String getOriginalPaperTitle() { return originalPaperTitle; }
    public void setOriginalPaperTitle(String value) { originalPaperTitle=value; }
    public String getNewPaperTitle() { return newPaperTitle; }
    public void setNewPaperTitle(String value) { newPaperTitle=value; }
    public Boolean getDoiMatch() { return doiMatch; }
    public void setDoiMatch(Boolean value) { doiMatch=value; }
    public Boolean getFileHashMatch() { return fileHashMatch; }
    public void setFileHashMatch(Boolean value) { fileHashMatch=value; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime value) { submittedAt=value; }
    public BigDecimal getTitleSimilarity() { return titleSimilarity; }
    public void setTitleSimilarity(BigDecimal value) { titleSimilarity=value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
    public Long getHandlerId() { return handlerId; }
    public void setHandlerId(Long value) { handlerId=value; }
    public String getHandlerNote() { return handlerNote; }
    public void setHandlerNote(String value) { handlerNote=value; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime value) { handledAt=value; }
}

