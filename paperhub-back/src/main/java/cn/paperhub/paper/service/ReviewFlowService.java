package cn.paperhub.paper.service;

import cn.paperhub.audit.service.AuditService;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.knowledge.service.KnowledgeLocks;
import cn.paperhub.paper.dto.ReviewFlowRequest;
import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.vo.PaperView;
import cn.paperhub.security.SessionPrincipal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewFlowService {
    private final SharedCatalogLock catalog;
    private final KnowledgeLocks knowledge;
    private final PaperWriteSupport writes;
    private final DuplicateDetectionService duplicates;
    private final PaperListingService listing;
    private final AuditService audit;
    private final Clock clock;
    public ReviewFlowService(SharedCatalogLock catalog,KnowledgeLocks knowledge,PaperWriteSupport writes,
            DuplicateDetectionService duplicates,PaperListingService listing,AuditService audit,Clock clock) {
        this.catalog=catalog; this.knowledge=knowledge; this.writes=writes; this.duplicates=duplicates;
        this.listing=listing; this.audit=audit; this.clock=clock;
    }
    @Transactional(timeout=60)
    public PaperView change(long id,ReviewFlowRequest input,SessionPrincipal actor) {
        PaperValidation.id(id); catalog.acquire(); knowledge.beforePaperWrite();
        PaperEntity paper=writes.active(id);
        if(!paper.getUploaderId().equals(actor.getId())) {
            if(!"admin".equals(actor.getRole()) && !("shared".equals(paper.getVisibility()) && "approved".equals(paper.getReviewStatus())))
                throw new BusinessException(ErrorCode.NOT_FOUND);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        String action=input.getAction();
        if("unshare".equals(action)) {
            if(!"shared".equals(paper.getVisibility()) ||
                    !Arrays.asList("pending","approved").contains(paper.getReviewStatus())) conflict();
            paper.setReviewStatus("offline"); paper.setOfflineReason("withdrawn"); paper.setReviewComment(null);
        } else {
            if("share".equals(action)) {
                if(!"private".equals(paper.getVisibility())) conflict();
                if(!Boolean.TRUE.equals(input.getCopyrightAgreed())) reject("私有论文共享必须重新确认版权");
                paper.setVisibility("shared");
            } else if("resubmit".equals(action)) {
                if("offline".equals(paper.getReviewStatus()) && "moderation".equals(paper.getOfflineReason()))
                    reject("管理员下架的论文不能由上传者重新提交");
                if(!"shared".equals(paper.getVisibility()) || !("rejected".equals(paper.getReviewStatus()) ||
                        ("offline".equals(paper.getReviewStatus()) && "withdrawn".equals(paper.getOfflineReason())))) conflict();
            } else throw new BusinessException(ErrorCode.INVALID_PARAM);
            if(Boolean.FALSE.equals(input.getCopyrightAgreed()) ||
                    (!Boolean.TRUE.equals(input.getCopyrightAgreed()) && !Boolean.TRUE.equals(paper.getCopyrightAgreed())))
                reject("请确认版权后重新提交");
            if(Boolean.TRUE.equals(input.getCopyrightAgreed())) {
                paper.setCopyrightAgreed(true); paper.setCopyrightAgreedAt(LocalDateTime.now(clock));
            }
            writes.publication(paper); duplicates.requireUnique(paper); duplicates.requireNoKnownDuplicate(paper);
            paper.setReviewStatus("pending"); paper.setOfflineReason(null); paper.setReviewComment(null);
        }
        writes.save(paper);
        if(!"unshare".equals(action)) duplicates.discover(paper,actor);
        audit.mutation(actor,"共享流转","paperId="+id+";ownerId="+paper.getUploaderId()+";action="+action);
        return listing.views(Collections.singletonList(paper),actor,"reviewFlowMetadata").get(0);
    }
    private void conflict() { throw new BusinessException(ErrorCode.CONFLICT); }
    private void reject(String message) { throw new BusinessException(ErrorCode.BIZ_REJECTED,message); }
}

