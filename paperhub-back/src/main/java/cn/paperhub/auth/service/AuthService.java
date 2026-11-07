package cn.paperhub.auth.service;

import cn.paperhub.auth.dto.LoginRequest;
import cn.paperhub.auth.entity.UserAccount;
import cn.paperhub.auth.mapper.UserAccountMapper;
import cn.paperhub.auth.vo.LoginResponse;
import cn.paperhub.auth.vo.LoginUser;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final String LOGIN_FAILED = "账号或密码错误，或当前登录入口不可用";
    private final UserAccountMapper users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final String dummyHash;

    public AuthService(UserAccountMapper users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "密码长度超过允许范围");
        }
        List<UserAccount> matches = users.findByLogin(request.getUsername().trim());
        UserAccount account = matches.size() == 1 ? matches.get(0) : null;
        boolean passwordMatches = encoder.matches(request.getPassword(), account == null ? dummyHash : account.getPassword());
        if (!passwordMatches || account == null || !"active".equals(account.getStatus())
                || !request.getRole().equals(account.getRole())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, LOGIN_FAILED);
        }
        return new LoginResponse(jwt.issue(account), new LoginUser(account));
    }
}
