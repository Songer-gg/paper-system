package cn.paperhub.admin.service;

import cn.paperhub.admin.dto.AdminUserQuery;
import cn.paperhub.admin.mapper.AdminUserMapper;
import cn.paperhub.admin.vo.AdminUserView;
import cn.paperhub.audit.service.AuditService;
import cn.paperhub.auth.entity.UserAccount;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.paper.service.PaperValidation;
import cn.paperhub.profile.mapper.AccountWriteMapper;
import cn.paperhub.profile.service.AccountValidation;
import cn.paperhub.security.SessionPrincipal;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
    private final AdminUserMapper users;
    private final AccountWriteMapper writes;
    private final AccountValidation validation;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final SecureRandom random=new SecureRandom();
    public AdminUserService(AdminUserMapper users,AccountWriteMapper writes,AccountValidation validation,
                            AuditService audit,PasswordEncoder encoder,Clock clock) {
        this.users=users;this.writes=writes;this.validation=validation;this.audit=audit;this.encoder=encoder;this.clock=clock;
    }
    @Transactional(readOnly=true)
    public List<AdminUserView> list(AdminUserQuery query,SessionPrincipal actor) {
        requireAdmin(actor);
        LocalDateTime from=date(query.getDateFrom(),false),to=date(query.getDateTo(),true);
        if(from!=null && to!=null && from.isAfter(to)) PaperValidation.invalid();
        List<AdminUserView> result=users.list(validation.keyword(query.getKeyword()),query.getStatus(),from,to);
        Map<Long,Long> owners=new LinkedHashMap<>();for(AdminUserView row:result) owners.put(row.id,row.id);
        if(owners.isEmpty()) owners.put(actor.getId(),actor.getId());
        audit.sensitiveResources(actor,"user",owners,"adminUserList",true); return result;
    }
    @Transactional(readOnly=true)
    public AdminUserView detail(long id,SessionPrincipal actor) {
        requireAdmin(actor);PaperValidation.id(id);AdminUserView result=users.detail(id);
        audit.sensitiveResources(actor,"user",Collections.singletonMap(id,id),"adminUserDetail",result!=null);
        if(result==null) throw new BusinessException(ErrorCode.NOT_FOUND);return result;
    }
    @Transactional(timeout=60)
    public boolean status(long id,String status,SessionPrincipal actor) {
        requireAdmin(actor);PaperValidation.id(id);
        if(!Arrays.asList("active","disabled").contains(status)) PaperValidation.invalid();
        List<UserAccount> administrators=writes.lockAdministrators();
        currentAdministrator(administrators,actor);
        UserAccount target=lockedTarget(administrators,id);
        if("disabled".equals(status)) {
            if(id==actor.getId()) throw new BusinessException(ErrorCode.BIZ_REJECTED,"不能禁用当前管理员");
            long active=administrators.stream().filter(u->"active".equals(u.getStatus())).count();
            if("admin".equals(target.getRole()) && "active".equals(target.getStatus()) && active<=1)
                throw new BusinessException(ErrorCode.BIZ_REJECTED,"不能禁用最后一个可用管理员");
        }
        if(!status.equals(target.getStatus())) {
            if(writes.status(id,status)!=1) throw new BusinessException(ErrorCode.CONFLICT);
            audit.mutation(actor,"disabled".equals(status)?"用户禁用":"用户恢复","ownerId="+id);
        }
        return true;
    }
    @Transactional(timeout=60)
    public String reset(long id,SessionPrincipal actor) {
        requireAdmin(actor);PaperValidation.id(id);
        List<UserAccount> administrators=writes.lockAdministrators();
        currentAdministrator(administrators,actor);lockedTarget(administrators,id);
        // 144 bits of entropy; generated only for this response, never persisted or logged in plaintext.
        byte[] bytes=new byte[18];random.nextBytes(bytes);
        String password=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        if(writes.reset(id,encoder.encode(password),LocalDateTime.now(clock).withNano(0))!=1)
            throw new BusinessException(ErrorCode.CONFLICT);
        audit.mutation(actor,"重置密码","ownerId="+id);return password;
    }
    public static void requireAdmin(SessionPrincipal actor) {
        if(actor==null || !"admin".equals(actor.getRole())) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    private void currentAdministrator(List<UserAccount> rows,SessionPrincipal actor) {
        for(UserAccount row:rows) if(row.getId()==actor.getId() && "active".equals(row.getStatus())) return;
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    private UserAccount lockedTarget(List<UserAccount> administrators,long id) {
        for(UserAccount row:administrators) if(row.getId()==id) return row;
        UserAccount target=writes.lock(id);
        if(target==null) throw new BusinessException(ErrorCode.NOT_FOUND);return target;
    }
    private LocalDateTime date(String input,boolean end) {
        if(input==null) return null;
        try {
            if(!input.matches("\\d{4}-\\d{2}-\\d{2}")) PaperValidation.invalid();
            LocalDate day=LocalDate.parse(input);
            if(day.getYear()<1000) PaperValidation.invalid();
            return end?day.atTime(23,59,59):day.atStartOfDay();
        } catch(java.time.DateTimeException ex) { PaperValidation.invalid();return null; }
    }
}
