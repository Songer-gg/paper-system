package cn.paperhub.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("report")
public class ReportEntity {
    @TableId(type=IdType.AUTO) private Long id;
    private Long paperId,reporterId,handlerId;
    private String paperTitle,type,reason,status,result;
    private LocalDateTime createdAt,handledAt;
    @TableField(exist=false) private String reporterName;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getPaperId() { return paperId; }
    public void setPaperId(Long value) { paperId=value; }
    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long value) { reporterId=value; }
    public Long getHandlerId() { return handlerId; }
    public void setHandlerId(Long value) { handlerId=value; }
    public String getPaperTitle() { return paperTitle; }
    public void setPaperTitle(String value) { paperTitle=value; }
    public String getType() { return type; }
    public void setType(String value) { type=value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
    public String getResult() { return result; }
    public void setResult(String value) { result=value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt=value; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime value) { handledAt=value; }
    public String getReporterName() { return reporterName; }
    public void setReporterName(String value) { reporterName=value; }
}
