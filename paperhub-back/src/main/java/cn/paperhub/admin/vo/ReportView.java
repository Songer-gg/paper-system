package cn.paperhub.admin.vo;

import cn.paperhub.admin.entity.ReportEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ReportView {
    public final Long id,paperId,reporterId;
    public final String type,paperTitle,reporterName,reason,status,result;
    public final LocalDateTime createdAt,handledAt;
    public ReportView(ReportEntity row) {
        id=row.getId();paperId=row.getPaperId();reporterId=row.getReporterId();type=row.getType();
        paperTitle=row.getPaperTitle();reporterName=row.getReporterName()==null?"":row.getReporterName();
        reason=row.getReason();status=row.getStatus();createdAt=row.getCreatedAt();
        result="resolved".equals(status)?row.getResult():null;handledAt="resolved".equals(status)?row.getHandledAt():null;
    }
}
