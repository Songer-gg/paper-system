package cn.paperhub.reading.service;

import cn.paperhub.audit.service.AuditService;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.mapper.PaperMapper;
import cn.paperhub.paper.service.*;
import cn.paperhub.security.SessionPrincipal;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PersonalPaperAccess {
    private final PaperMapper papers;
    private final AuditService audit;
    public PersonalPaperAccess(PaperMapper papers,AuditService audit) { this.papers=papers; this.audit=audit; }
    public PaperEntity lock(long id) { PaperValidation.id(id); return papers.lockById(id); }
    public PaperEntity visible(PaperEntity paper,SessionPrincipal actor,String action) {
        boolean allowed=PaperVisibility.allowed(paper,actor);
        sensitive(paper,actor,action,allowed);
        if(!allowed) throw new BusinessException(ErrorCode.NOT_FOUND);
        return paper;
    }
    public void sensitive(PaperEntity paper,SessionPrincipal actor,String action,boolean success) {
        if(paper!=null && "admin".equals(actor.getRole()) && "private".equals(paper.getVisibility()))
            audit.sensitive(actor,paper.getId(),paper.getUploaderId(),action,success);
    }
}

