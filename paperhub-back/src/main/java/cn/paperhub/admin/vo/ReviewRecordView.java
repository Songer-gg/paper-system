package cn.paperhub.admin.vo;

import cn.paperhub.admin.entity.ReviewRecordEntity;
import java.time.LocalDateTime;

public final class ReviewRecordView {
    public final Long id,paperId,uploaderId,reviewerId;
    public final String paperTitle,uploaderName,reviewerName,action,comment;
    public final LocalDateTime reviewedAt;
    public ReviewRecordView(ReviewRecordEntity record,String uploader,String reviewer) {
        id=record.getId(); paperId=record.getPaperId(); uploaderId=record.getUploaderId(); reviewerId=record.getReviewerId();
        paperTitle=record.getPaperTitle(); uploaderName=uploader; reviewerName=reviewer; action=record.getAction();
        comment=record.getComment()==null?"":record.getComment(); reviewedAt=record.getReviewedAt();
    }
}

