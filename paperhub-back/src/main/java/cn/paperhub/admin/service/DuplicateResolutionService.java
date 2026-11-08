package cn.paperhub.admin.service;

import cn.paperhub.admin.entity.DuplicatePairEntity;
import cn.paperhub.admin.mapper.DuplicatePairMapper;
import cn.paperhub.audit.service.AuditService;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.knowledge.service.KnowledgeLocks;
import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.mapper.PaperMapper;
import cn.paperhub.paper.service.*;
import cn.paperhub.security.SessionPrincipal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DuplicateResolutionService {
    private final SharedCatalogLock catalog;
    private final KnowledgeLocks knowledge;
    private final DuplicatePairMapper pairs;
    private final PaperMapper papers;
    private final PaperWriteSupport writes;
    private final DuplicateDetectionService detection;
    private final ReviewRecordStore records;
    private final AuditService audit;
    private final Clock clock;
    public DuplicateResolutionService(SharedCatalogLock catalog,KnowledgeLocks knowledge,DuplicatePairMapper pairs,
            PaperMapper papers,PaperWriteSupport writes,DuplicateDetectionService detection,ReviewRecordStore records,
            AuditService audit,Clock clock) {
        this.catalog=catalog; this.knowledge=knowledge; this.pairs=pairs; this.papers=papers; this.writes=writes;
        this.detection=detection; this.records=records; this.audit=audit; this.clock=clock;
    }
    @Transactional(timeout=60)
    public boolean resolve(long id,String action,String note,SessionPrincipal actor) {
        PaperValidation.id(id);
        if(!Arrays.asList("duplicate","distinct","linked").contains(action)) throw new BusinessException(ErrorCode.INVALID_PARAM);
        if(note!=null && note.length()>500) throw new BusinessException(ErrorCode.INVALID_PARAM);
        catalog.acquire(); knowledge.beforePaperWrite();
        DuplicatePairEntity initial=pairs.selectById(id);
        if(initial==null) throw new BusinessException(ErrorCode.NOT_FOUND);
        List<Long> ids=new ArrayList<>(Arrays.asList(initial.getOriginalPaperId(),initial.getNewPaperId()));
        Collections.sort(ids); Map<Long,PaperEntity> locked=new HashMap<>();
        for(Long paperId:ids) locked.put(paperId,papers.lockById(paperId));
        DuplicatePairEntity pair=pairs.lock(id);
        if(!"pending".equals(pair.getStatus())) throw new BusinessException(ErrorCode.CONFLICT);
        PaperEntity original=locked.get(pair.getOriginalPaperId()),newer=locked.get(pair.getNewPaperId());
        if(original==null || newer==null) throw new BusinessException(ErrorCode.CONFLICT,"历史关联不完整");
        Map<Long,Long> privateOwners=new LinkedHashMap<>();
        for(PaperEntity paper:locked.values()) if("private".equals(paper.getVisibility())) privateOwners.put(paper.getId(),paper.getUploaderId());
        if(!privateOwners.isEmpty()) audit.sensitiveResources(actor,"paper",privateOwners,"duplicateResolutionAccess",true);
        if("distinct".equals(action)) {
            if(detection.sameIdentity(original,newer,true) || detection.sameIdentity(original,newer,false))
                reject("相同 DOI 或 PDF 不能标记为非重复");
        } else {
            if("linked".equals(action) && (original.getDeletedAt()!=null || !"shared".equals(original.getVisibility())
                    || !"approved".equals(original.getReviewStatus()))) reject("只能关联到已通过的有效共享论文");
            // Preserve withdrawn/moderated/deleted states while still closing their historical evidence once.
            if(newer.getDeletedAt()==null && "shared".equals(newer.getVisibility()) &&
                    Arrays.asList("pending","approved").contains(newer.getReviewStatus())) {
                String reason="linked".equals(action)?"已关联到原共享资源 #"+original.getId()+"，请使用已有资源":"重复共享资源，已驳回，请使用已有资源";
                newer.setReviewStatus("rejected"); newer.setOfflineReason(null); newer.setReviewComment(reason);
                writes.save(newer); records.append(newer,actor,"rejected",reason);
            }
        }
        if(pairs.resolve(id,action,actor.getId(),note==null?"":note.trim(),LocalDateTime.now(clock))!=1)
            throw new BusinessException(ErrorCode.CONFLICT);
        audit.mutation(actor,"重复论文处理","pairId="+id+";originalPaperId="+original.getId()+
                ";newPaperId="+newer.getId()+";action="+action);
        return true;
    }
    private void reject(String message) { throw new BusinessException(ErrorCode.BIZ_REJECTED,message); }
}

