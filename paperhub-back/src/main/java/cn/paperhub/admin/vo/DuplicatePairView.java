package cn.paperhub.admin.vo;

import cn.paperhub.admin.entity.DuplicatePairEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.math.BigDecimal;

public final class DuplicatePairView {
    public final Long id,originalPaperId,newPaperId;
    public final boolean doiMatch,fileHashMatch;
    public final BigDecimal titleSimilarity;
    public final String status;
    public final LocalDateTime submittedAt;
    @JsonInclude(JsonInclude.Include.NON_NULL) public final LocalDateTime handledAt;
    @JsonInclude(JsonInclude.Include.NON_NULL) public final String handlerNote;
    public DuplicatePairView(DuplicatePairEntity pair) {
        id=pair.getId(); originalPaperId=pair.getOriginalPaperId(); newPaperId=pair.getNewPaperId();
        doiMatch=Boolean.TRUE.equals(pair.getDoiMatch()); fileHashMatch=Boolean.TRUE.equals(pair.getFileHashMatch());
        titleSimilarity=pair.getTitleSimilarity(); status=pair.getStatus(); submittedAt=pair.getSubmittedAt();
        handledAt=pair.getHandledAt(); handlerNote=pair.getHandlerNote();
    }
}

