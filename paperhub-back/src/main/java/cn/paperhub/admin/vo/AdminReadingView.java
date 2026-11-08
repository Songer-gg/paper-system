package cn.paperhub.admin.vo;

import cn.paperhub.paper.vo.PaperView;
import cn.paperhub.reading.entity.ReadingEntity;
import java.time.LocalDateTime;

public final class AdminReadingView {
    public final Long paperId;
    public final String status;
    public final LocalDateTime addedAt,lastReadAt;
    public final PaperView paper;
    public AdminReadingView(ReadingEntity row,PaperView paper) {
        paperId=row.getPaperId();status=row.getStatus();addedAt=row.getAddedAt();lastReadAt=row.getLastReadAt();this.paper=paper;
    }
}
