package cn.paperhub.profile.service;

import cn.paperhub.auth.entity.UserAccount;
import cn.paperhub.auth.mapper.UserAccountMapper;
import cn.paperhub.audit.service.AuditService;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.profile.dto.*;
import cn.paperhub.profile.mapper.AccountWriteMapper;
import cn.paperhub.profile.vo.*;
import cn.paperhub.security.SessionPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final UserAccountMapper users;
    private final AccountWriteMapper writes;
    private final AccountValidation validation;
    private final PasswordEncoder encoder;
    private final AvatarService avatars;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    public ProfileService(UserAccountMapper users,AccountWriteMapper writes,AccountValidation validation,
                          PasswordEncoder encoder,AvatarService avatars,AuditService audit,JdbcTemplate jdbc) {
        this.users=users;this.writes=writes;this.validation=validation;this.encoder=encoder;
        this.avatars=avatars;this.audit=audit;this.jdbc=jdbc;
    }
    @Transactional(readOnly=true)
    public MyProfileView get(SessionPrincipal actor) { return view(active(users.selectById(actor.getId()))); }

    @Transactional(timeout=60)
    public MyProfileView edit(ProfilePatch patch,SessionPrincipal actor) {
        if(patch.empty()) throw new BusinessException(ErrorCode.INVALID_PARAM);
        String name=patch.has("name")?validation.name(patch.getName()):null;
        String email=patch.has("email")?validation.email(patch.getEmail()):null;
        UserAccount user=active(writes.lock(actor.getId()));
        if(email!=null && jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE id<>? AND (email=? OR username=?)",
                Long.class,user.getId(),email,email)>0) throw new BusinessException(ErrorCode.CONFLICT,"邮箱已被使用");
        if(name==null) name=user.getName(); if(email==null) email=user.getEmail();
        if(!name.equals(user.getName()) || !email.equals(user.getEmail())) {
            if(writes.profile(user.getId(),name,email)!=1) throw new BusinessException(ErrorCode.CONFLICT);
            user.setName(name);user.setEmail(email);
            audit.mutation(actor,"资料修改","ownerId="+actor.getId());
        }
        return view(user);
    }
    @Transactional(timeout=60)
    public PasswordView password(PasswordChange input,SessionPrincipal actor) {
        validation.password(input.getOldPassword(),false); validation.password(input.getNewPassword(),true);
        if(!input.getNewPassword().equals(input.getConfirmPassword()))
            throw new BusinessException(ErrorCode.BIZ_REJECTED,"两次新密码不一致");
        UserAccount user=active(writes.lock(actor.getId()));
        if(!encoder.matches(input.getOldPassword(),user.getPassword()))
            throw new BusinessException(ErrorCode.BIZ_REJECTED,"原密码不正确");
        if(encoder.matches(input.getNewPassword(),user.getPassword()))
            throw new BusinessException(ErrorCode.BIZ_REJECTED,"新密码不能与原密码相同");
        if(writes.password(user.getId(),encoder.encode(input.getNewPassword()))!=1)
            throw new BusinessException(ErrorCode.CONFLICT);
        audit.mutation(actor,"密码修改","ownerId="+actor.getId()); return new PasswordView();
    }
    private MyProfileView view(UserAccount user) {
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM paper WHERE uploader_id=? AND deleted_at IS NULL",Long.class,user.getId());
        return new MyProfileView(user,avatars.display(user.getAvatar()),count);
    }
    private UserAccount active(UserAccount user) {
        if(user==null || !"active".equals(user.getStatus())) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return user;
    }
}
