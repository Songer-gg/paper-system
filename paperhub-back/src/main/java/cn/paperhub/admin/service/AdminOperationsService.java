package cn.paperhub.admin.service;

import cn.paperhub.admin.mapper.OperationLogMapper;
import cn.paperhub.admin.vo.*;
import cn.paperhub.audit.service.AuditService;
import cn.paperhub.security.SessionPrincipal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOperationsService {
    private final OperationLogMapper logs;
    private final AuditService audit;
    private final ModerationValidation validation;
    public AdminOperationsService(OperationLogMapper logs,AuditService audit,ModerationValidation validation) {
        this.logs=logs;this.audit=audit;this.validation=validation;
    }
    @Transactional(readOnly=true)
    public List<OperationLogView> logs(String action,SessionPrincipal actor) {
        AdminUserService.requireAdmin(actor);String clean=validation.action(action);
        audit.collection(actor,"operationLogs","logList");return logs.list(clean);
    }
    @Transactional(readOnly=true)
    public PlatformSettingsView settings(SessionPrincipal actor) {
        AdminUserService.requireAdmin(actor);audit.collection(actor,"platformSettings","settingsView");
        return new PlatformSettingsView();
    }
}
