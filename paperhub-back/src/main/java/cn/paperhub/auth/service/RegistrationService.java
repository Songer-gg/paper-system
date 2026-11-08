package cn.paperhub.auth.service;

import cn.paperhub.audit.service.AuditService;
import cn.paperhub.auth.dto.RegisterRequest;
import cn.paperhub.auth.entity.UserAccount;
import cn.paperhub.auth.mapper.UserAccountMapper;
import cn.paperhub.auth.vo.LoginUser;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.profile.service.AccountValidation;
import cn.paperhub.security.SessionPrincipal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final UserAccountMapper users;
    private final AccountValidation validation;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final Clock clock;

    public RegistrationService(UserAccountMapper users, AccountValidation validation,
            PasswordEncoder encoder, AuditService audit, Clock clock) {
        this.users = users; this.validation = validation; this.encoder = encoder;
        this.audit = audit; this.clock = clock;
    }

    @Transactional(timeout = 60)
    public LoginUser register(RegisterRequest input) {
        String username = input.getUsername().trim().toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z][a-z0-9_]{2,49}"))
            throw new BusinessException(ErrorCode.INVALID_PARAM, "用户名须以字母开头，包含3–50位字母、数字或下划线");
        String name = validation.name(input.getName());
        String email = validation.email(input.getEmail());
        validation.password(input.getPassword(), true);
        if (!input.getPassword().equals(input.getConfirmPassword()))
            throw new BusinessException(ErrorCode.INVALID_PARAM, "两次密码输入不一致");
        if (users.countLoginIdentifiers(username, email) > 0) throw duplicate();
        UserAccount account = new UserAccount();
        account.setUsername(username); account.setName(name); account.setEmail(email);
        account.setPassword(encoder.encode(input.getPassword()));
        account.setRole("user"); account.setStatus("active"); account.setTokenVersion(0L);
        account.setRegisteredAt(LocalDateTime.now(clock));
        try {
            if (users.insert(account) != 1) throw new BusinessException(ErrorCode.SERVER_ERROR);
        } catch (DuplicateKeyException ex) {
            // The unique indexes also arbitrate concurrent registrations; never echo SQL or credentials.
            throw duplicate();
        }
        audit.mutation(new SessionPrincipal(account.getId(), username, "user"),
                "用户注册", "ownerId=" + account.getId());
        return new LoginUser(account);
    }

    private BusinessException duplicate() {
        return new BusinessException(ErrorCode.CONFLICT, "用户名或邮箱已被使用");
    }
}
