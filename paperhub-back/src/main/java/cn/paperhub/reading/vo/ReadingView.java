package cn.paperhub.reading.vo;

import cn.paperhub.reading.entity.ReadingEntity;
import java.time.LocalDateTime;

public final class ReadingView {
    public final Long paperId;
    public final String status;
    public final LocalDateTime addedAt,lastReadAt;
    public ReadingView(ReadingEntity row) {
        paperId=row.getPaperId(); status=row.getStatus(); addedAt=row.getAddedAt(); lastReadAt=row.getLastReadAt();
    }
}

