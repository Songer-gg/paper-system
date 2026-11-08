package cn.paperhub.admin.vo;

import cn.paperhub.admin.entity.DuplicatePairEntity;
import java.time.LocalDateTime;

public final class DuplicateRecordView {
    public final Long id,pairId,handlerId;
    public final String paperTitle,action,note,handlerName;
    public final LocalDateTime handledAt;
    public DuplicateRecordView(DuplicatePairEntity pair,String handlerName) {
        id=pair.getId(); pairId=pair.getId(); handlerId=pair.getHandlerId(); paperTitle=pair.getNewPaperTitle();
        action=pair.getStatus(); note=pair.getHandlerNote()==null?"":pair.getHandlerNote();
        this.handlerName=handlerName; handledAt=pair.getHandledAt();
    }
}

