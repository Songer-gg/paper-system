package cn.paperhub.paper.service;

import cn.paperhub.admin.entity.DuplicatePairEntity;
import cn.paperhub.admin.mapper.DuplicatePairMapper;
import cn.paperhub.audit.service.AuditService;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.paper.dto.DuplicateCheckRequest;
import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.mapper.PaperMapper;
import cn.paperhub.paper.vo.*;
import cn.paperhub.security.SessionPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DuplicateDetectionService {
    private final PaperMapper papers;
    private final DuplicatePairMapper pairs;
    private final PaperValidation validation;
    private final PaperListingService listing;
    private final AuditService audit;
    private final Clock clock;
    public DuplicateDetectionService(PaperMapper papers,DuplicatePairMapper pairs,PaperValidation validation,
            PaperListingService listing,AuditService audit,Clock clock) {
        this.papers=papers; this.pairs=pairs; this.validation=validation; this.listing=listing; this.audit=audit; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public DuplicateCheckView check(DuplicateCheckRequest input,SessionPrincipal actor) {
        String doi=validation.normalizeDoi(input.getDoi());
        String hash=input.getFileSha256()==null?null:input.getFileSha256().toLowerCase(Locale.ROOT);
        List<PaperEntity> candidates=candidates();
        if(doi!=null) for(PaperEntity candidate:candidates)
            if(doi.equals(normalized(candidate))) return match("doi","存在相同 DOI 的共享记录",candidate,actor);
        if(hash!=null) for(PaperEntity candidate:candidates)
            if(hash.equals(candidate.getFileSha256())) return match("file","存在相同 PDF 的共享记录",candidate,actor);
        if(input.getTitle()!=null && !input.getTitle().trim().isEmpty()) {
            PaperEntity best=null; double score=0.80;
            for(PaperEntity candidate:candidates) {
                double value=PaperService.titleSimilarity(input.getTitle(),candidate.getTitle());
                if(value>=score && (best==null || value>score)) { best=candidate; score=value; }
            }
            if(best!=null) return match("title","存在标题相似的共享记录，提交后需人工判断",best,actor);
        }
        return new DuplicateCheckView("none","未发现重复共享记录",null);
    }
    public void requireUnique(PaperEntity paper) {
        String doi=normalized(paper);
        for(PaperEntity candidate:candidates()) {
            if(Objects.equals(candidate.getId(),paper.getId())) continue;
            if((doi!=null && doi.equals(normalized(candidate))) ||
                    (paper.getFileSha256()!=null && paper.getFileSha256().equals(candidate.getFileSha256())))
                throw new BusinessException(ErrorCode.CONFLICT,"存在相同 DOI 或 PDF 的有效共享记录");
        }
    }
    public void requireNoKnownDuplicate(PaperEntity paper) {
        if(paper.getId()==null) return;
        for(DuplicatePairEntity pair:pairs.selectList(new QueryWrapper<DuplicatePairEntity>()
                .eq("new_paper_id",paper.getId()).in("status",Arrays.asList("duplicate","linked")))) {
            PaperEntity original=papers.selectById(pair.getOriginalPaperId());
            if(original!=null && original.getDeletedAt()==null && "shared".equals(original.getVisibility())
                    && !"rejected".equals(original.getReviewStatus()))
                throw new BusinessException(ErrorCode.CONFLICT,"此资源已确认重复，请使用原共享资源或上传正确版本");
        }
    }
    /** Called while holding SharedCatalogLock; never overwrite any prior verdict or snapshots. */
    public int discover(PaperEntity paper,SessionPrincipal actor) {
        int created=0;
        for(PaperEntity candidate:candidates()) {
            if(Objects.equals(candidate.getId(),paper.getId())) continue;
            double score=PaperService.titleSimilarity(candidate.getTitle(),paper.getTitle());
            if(score<0.80) continue;
            Long existing=pairs.selectCount(new QueryWrapper<DuplicatePairEntity>().and(q->
                    q.eq("original_paper_id",candidate.getId()).eq("new_paper_id",paper.getId())
                    .or(reverse->reverse.eq("original_paper_id",paper.getId()).eq("new_paper_id",candidate.getId()))));
            if(existing>0) continue;
            DuplicatePairEntity pair=new DuplicatePairEntity();
            pair.setOriginalPaperId(candidate.getId()); pair.setNewPaperId(paper.getId());
            pair.setOriginalPaperTitle(candidate.getTitle()); pair.setNewPaperTitle(paper.getTitle());
            pair.setDoiMatch(sameIdentity(paper,candidate,true)); pair.setFileHashMatch(sameIdentity(paper,candidate,false));
            pair.setTitleSimilarity(BigDecimal.valueOf(score*100).setScale(2,RoundingMode.HALF_UP));
            pair.setReason("标题相似，需人工判断"); pair.setStatus("pending"); pair.setSubmittedAt(LocalDateTime.now(clock));
            pairs.insert(pair); PaperValidation.id(pair.getId()); created++;
        }
        if(created>0) audit.mutation(actor,"重复检测","paperId="+paper.getId()+";newPairCount="+created);
        return created;
    }
    public boolean pending(long paperId) {
        return pairs.selectCount(new QueryWrapper<DuplicatePairEntity>().eq("status","pending")
                .and(q->q.eq("original_paper_id",paperId).or().eq("new_paper_id",paperId)))>0;
    }
    public boolean sameIdentity(PaperEntity one,PaperEntity two,boolean doi) {
        String a=doi?normalized(one):one.getFileSha256(),b=doi?normalized(two):two.getFileSha256();
        return a!=null && a.equals(b);
    }
    private String normalized(PaperEntity paper) { return validation.normalizeDoi(paper.getDoi()); }
    private List<PaperEntity> candidates() {
        return papers.selectList(new QueryWrapper<PaperEntity>().eq("visibility","shared").isNull("deleted_at")
                .ne("review_status","rejected").select("id","title","doi","doi_normalized","file_sha256","uploader_id",
                        "visibility","review_status").orderByAsc("id"));
    }
    private DuplicateCheckView match(String level,String message,PaperEntity paper,SessionPrincipal actor) {
        boolean visible=paper.getUploaderId().equals(actor.getId()) || "admin".equals(actor.getRole())
                || "approved".equals(paper.getReviewStatus());
        PaperView view=null;
        if(visible) view=listing.views(Collections.singletonList(papers.selectById(paper.getId())),actor,"duplicateHint").get(0);
        return new DuplicateCheckView(level,message,view);
    }
}

