package cn.paperhub.admin.service;

import cn.paperhub.admin.entity.ReviewRecordEntity;
import cn.paperhub.admin.mapper.ReviewRecordMapper;
import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.service.PaperValidation;
import cn.paperhub.security.SessionPrincipal;
import java.time.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class ReviewRecordStore {
    private final ReviewRecordMapper mapper;
    private final Clock clock;
    public ReviewRecordStore(ReviewRecordMapper mapper,Clock clock) { this.mapper=mapper; this.clock=clock; }
    @Transactional(propagation=Propagation.MANDATORY)
    public void append(PaperEntity paper,SessionPrincipal actor,String action,String comment) {
        ReviewRecordEntity record=new ReviewRecordEntity();
        record.setPaperId(paper.getId()); record.setPaperTitle(paper.getTitle()); record.setUploaderId(paper.getUploaderId());
        record.setReviewerId(actor.getId()); record.setAction(action); record.setComment(comment);
        record.setReviewedAt(LocalDateTime.now(clock)); mapper.insert(record); PaperValidation.id(record.getId());
    }
}

