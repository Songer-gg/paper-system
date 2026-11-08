package cn.paperhub.admin.service;

import cn.paperhub.admin.entity.ReportEntity;
import cn.paperhub.admin.mapper.ReportMapper;
import cn.paperhub.admin.vo.ReportView;
import cn.paperhub.audit.service.AuditService;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.knowledge.service.KnowledgeLocks;
import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.mapper.PaperMapper;
import cn.paperhub.paper.service.*;
import cn.paperhub.security.SessionPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private final ReportMapper reports;
    private final PaperMapper papers;
    private final SharedCatalogLock catalog;
    private final KnowledgeLocks knowledge;
    private final PaperWriteSupport writes;
    private final AuditService audit;
    private final ModerationValidation validation;
    private final Clock clock;
    public ReportService(ReportMapper reports,PaperMapper papers,SharedCatalogLock catalog,KnowledgeLocks knowledge,
                         PaperWriteSupport writes,AuditService audit,ModerationValidation validation,Clock clock) {
        this.reports=reports;this.papers=papers;this.catalog=catalog;this.knowledge=knowledge;
        this.writes=writes;this.audit=audit;this.validation=validation;this.clock=clock;
    }
    @Transactional(readOnly=true)
    public List<ReportView> list(String status,boolean history,SessionPrincipal actor) {
        AdminUserService.requireAdmin(actor);
        if(!Arrays.asList("all","pending","resolved").contains(status)) throw new BusinessException(ErrorCode.INVALID_PARAM);
        audit.collection(actor,"reports",history?"reportHistory":"reportList:"+status);
        List<ReportEntity> rows=reports.list(status,history);
        Map<Long,Long> owners=new LinkedHashMap<>();Set<Long> ids=new LinkedHashSet<>();
        for(ReportEntity row:rows) { owners.put(row.getId(),row.getReporterId());ids.add(row.getPaperId()); }
        if(!owners.isEmpty()) audit.sensitiveResources(actor,"report",owners,"reportSnapshots",true);
        List<Long> paperIds=new ArrayList<>(ids);Map<Long,Long> privateOwners=new LinkedHashMap<>();
        for(int start=0;start<paperIds.size();start+=500)
            for(PaperEntity paper:papers.selectList(new QueryWrapper<PaperEntity>().eq("visibility","private")
                    .in("id",paperIds.subList(start,Math.min(start+500,paperIds.size()))).select("id","uploader_id")))
                privateOwners.put(paper.getId(),paper.getUploaderId());
        if(!privateOwners.isEmpty()) audit.sensitiveResources(actor,"paper",privateOwners,"reportPrivateSnapshots",true);
        List<ReportView> result=new ArrayList<>();for(ReportEntity row:rows) result.add(new ReportView(row));return result;
    }
    @Transactional(timeout=60)
    public boolean resolve(long id,String result,boolean offlineToo,SessionPrincipal actor) {
        AdminUserService.requireAdmin(actor);PaperValidation.id(id);String clean=validation.result(result);
        catalog.acquire();knowledge.beforePaperWrite();
        ReportEntity reference=reports.reference(id);
        if(reference==null) throw new BusinessException(ErrorCode.NOT_FOUND);
        PaperEntity paper=papers.lockById(reference.getPaperId());
        ReportEntity current=reports.lock(id);
        if(current==null) throw new BusinessException(ErrorCode.NOT_FOUND);
        if(!reference.getPaperId().equals(current.getPaperId()) || !"pending".equals(current.getStatus()))
            throw new BusinessException(ErrorCode.CONFLICT);
        if(paper==null) throw new BusinessException(ErrorCode.CONFLICT,"举报关联不完整");
        audit.sensitiveResources(actor,"report",Collections.singletonMap(id,current.getReporterId()),"reportResolutionAccess",true);
        if("private".equals(paper.getVisibility())) audit.sensitive(actor,paper.getId(),paper.getUploaderId(),"reportResolutionSnapshot",true);
        boolean publicPaper=paper.getDeletedAt()==null && "shared".equals(paper.getVisibility()) && "approved".equals(paper.getReviewStatus());
        if(offlineToo && publicPaper) {
            paper.setReviewStatus("offline");paper.setOfflineReason("moderation");paper.setReviewComment("举报核实后下架");
            writes.save(paper);
            audit.mutation(actor,"论文下架","paperId="+paper.getId()+";reportId="+id+";reason=moderation");
        } else if(offlineToo && paper.getDeletedAt()==null &&
                !("shared".equals(paper.getVisibility()) && "offline".equals(paper.getReviewStatus()))) {
            throw new BusinessException(ErrorCode.BIZ_REJECTED,"该论文不能执行共享下架，请取消同时下架后处理");
        }
        if(reports.resolve(id,paper.getId(),actor.getId(),clean,LocalDateTime.now(clock).withNano(0))!=1)
            throw new BusinessException(ErrorCode.CONFLICT);
        audit.mutation(actor,"举报处理","reportId="+id+";paperId="+paper.getId()+";offlineRequested="+offlineToo+";paperOfflined="+(offlineToo&&publicPaper));
        return true;
    }
}
