package cn.paperhub.reading.vo;

import cn.paperhub.paper.vo.PaperView;
import java.time.LocalDateTime;

public final class FavoriteView {
    public final Long paperId;
    public final PaperView paper;
    public final LocalDateTime favoritedAt;
    public FavoriteView(Long paperId,PaperView paper,LocalDateTime favoritedAt) {
        this.paperId=paperId; this.paper=paper; this.favoritedAt=favoritedAt;
    }
}

