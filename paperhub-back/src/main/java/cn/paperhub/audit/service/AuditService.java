package cn.paperhub.audit.service;

import cn.paperhub.audit.mapper.AuditMapper;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.security.SessionPrincipal;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AuditService {
    private final AuditMapper mapper;
    private final Clock clock;
    public AuditService(AuditMapper mapper, Clock clock) { this.mapper=mapper; this.clock=clock; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sensitive(SessionPrincipal actor, long paperId, long ownerId, String action, boolean success) {
        append(actor, "敏感数据访问", "paperId=" + paperId + ";ownerId=" + ownerId + ";action=" + action, success);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sensitiveResources(SessionPrincipal actor, String kind, java.util.Map<Long,Long> owners,
                                   String action, boolean success) {
        for (java.util.Map.Entry<Long,Long> resource : owners.entrySet())
            append(actor, "敏感数据访问", kind + "Id=" + resource.getKey() + ";ownerId=" + resource.getValue()
                    + ";action=" + action, success);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void mutation(SessionPrincipal actor, String action, String target) {
        append(actor, action, target, true);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void mutationResult(SessionPrincipal actor,String action,String target,boolean success) {
        append(actor,action,target,success);
    }
    private void append(SessionPrincipal actor, String action, String target, boolean success) {
        if (mapper.append(actor.getId(), action, target, success ? "成功" : "失败", LocalDateTime.now(clock)) != 1)
            throw new BusinessException(ErrorCode.SERVER_ERROR);
    }
}

