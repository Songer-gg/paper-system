package cn.paperhub.admin.service;

import cn.paperhub.audit.service.AuditService;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.knowledge.service.KnowledgeLocks;
import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.mapper.PaperMapper;
import cn.paperhub.paper.service.*;
import cn.paperhub.paper.vo.PaperView;
import cn.paperhub.security.SessionPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminReviewService {
    private final SharedCatalogLock catalog;
    private final KnowledgeLocks knowledge;
    private final PaperWriteSupport writes;
    private final DuplicateDetectionService duplicates;
    private final ReviewRecordStore records;
    private final AuditService audit;
    private final PaperMapper mapper;
    private final PaperListingService listing;
    public AdminReviewService(SharedCatalogLock catalog,KnowledgeLocks knowledge,PaperWriteSupport writes,
            DuplicateDetectionService duplicates,ReviewRecordStore records,AuditService audit,
            PaperMapper mapper,PaperListingService listing) {
        this.catalog=catalog; this.knowledge=knowledge; this.writes=writes; this.duplicates=duplicates;
        this.records=records; this.audit=audit; this.mapper=mapper; this.listing=listing;
    }
    @Transactional(timeout=60)
    public boolean approve(long id,String comment,Boolean confirmed,SessionPrincipal actor) {
        PaperEntity paper=lockedShared(id);
        if(!"pending".equals(paper.getReviewStatus())) conflict();
        if(!Boolean.TRUE.equals(confirmed)) reject("请确认版权声明");
        if(!ready(paper,actor,"论文审核")) return false;
        paper.setReviewStatus("approved"); paper.setOfflineReason(null); paper.setReviewComment(null);
        writes.save(paper); records.append(paper,actor,"approved",comment(comment));
        audit.mutation(actor,"论文审核","paperId="+id+";action=approved");
        return true;
    }
    @Transactional(timeout=60)
    public boolean reject(long id,String comment,SessionPrincipal actor) {
        PaperEntity paper=lockedShared(id);
        if(!"pending".equals(paper.getReviewStatus())) conflict();
        String reason=comment(comment);
        if(reason.isEmpty()) reject("请填写驳回原因");
        paper.setReviewStatus("rejected"); paper.setOfflineReason(null); paper.setReviewComment(reason);
        writes.save(paper); records.append(paper,actor,"rejected",reason);
        audit.mutation(actor,"论文审核","paperId="+id+";action=rejected");
        return true;
    }
    @Transactional(timeout=60)
    public boolean offline(long id,SessionPrincipal actor) {
        PaperEntity paper=lockedShared(id);
        if(!"approved".equals(paper.getReviewStatus())) conflict();
        paper.setReviewStatus("offline"); paper.setOfflineReason("moderation"); paper.setReviewComment("管理员下架");
        writes.save(paper); audit.mutation(actor,"论文下架","paperId="+id+";reason=moderation"); return true;
    }
    @Transactional(timeout=60)
    public boolean restore(long id,SessionPrincipal actor) {
        PaperEntity paper=lockedShared(id);
        if("withdrawn".equals(paper.getOfflineReason())) reject("上传者撤回的论文不能由管理员恢复");
        if(!"offline".equals(paper.getReviewStatus()) || !"moderation".equals(paper.getOfflineReason())) conflict();
        if(!ready(paper,actor,"论文恢复")) return false;
        paper.setReviewStatus("approved"); paper.setOfflineReason(null); paper.setReviewComment(null);
        writes.save(paper); audit.mutation(actor,"论文恢复","paperId="+id); return true;
    }
    @Transactional(readOnly=true)
    public Map<String,List<PaperView>> group(SessionPrincipal actor) {
        Map<String,List<PaperView>> result=new LinkedHashMap<>();
        result.put("pending",new ArrayList<>()); result.put("approved",new ArrayList<>()); result.put("rejected",new ArrayList<>());
        List<PaperEntity> values=mapper.selectList(new QueryWrapper<PaperEntity>().eq("visibility","shared").isNull("deleted_at")
                .in("review_status",result.keySet()).orderByDesc("uploaded_at","id"));
        for(PaperView view:listing.views(values,actor,"adminReviewGroup")) result.get(view.getReviewStatus()).add(view);
        return result;
    }
    private PaperEntity lockedShared(long id) {
        PaperValidation.id(id); catalog.acquire(); knowledge.beforePaperWrite();
        PaperEntity paper=writes.active(id);
        if(!"shared".equals(paper.getVisibility())) throw new BusinessException(ErrorCode.NOT_FOUND);
        return paper;
    }
    private boolean ready(PaperEntity paper,SessionPrincipal actor,String action) {
        writes.publication(paper); duplicates.requireUnique(paper); duplicates.requireNoKnownDuplicate(paper);
        duplicates.discover(paper,actor);
        if(duplicates.pending(paper.getId())) {
            // Return, commit the newly discovered evidence, then let the controller emit the business rejection.
            audit.mutationResult(actor,action,"paperId="+paper.getId()+";reason=pendingDuplicate",false);
            return false;
        }
        return true;
    }
    private String comment(String value) {
        if(value!=null && value.length()>500) throw new BusinessException(ErrorCode.INVALID_PARAM);
        if(value==null) return "";
        String clean=value.trim();
        return clean.codePoints().allMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)||c==0xFEFF)?"":clean;
    }
    private void conflict() { throw new BusinessException(ErrorCode.CONFLICT); }
    private void reject(String message) { throw new BusinessException(ErrorCode.BIZ_REJECTED,message); }
}

