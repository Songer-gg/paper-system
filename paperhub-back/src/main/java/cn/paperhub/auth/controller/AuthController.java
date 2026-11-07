package cn.paperhub.auth.controller;

import cn.paperhub.auth.dto.LoginRequest;
import cn.paperhub.auth.service.AuthService;
import cn.paperhub.auth.vo.LoginResponse;
import cn.paperhub.common.api.ApiResponse;
import cn.paperhub.security.LoginRateLimiter;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final LoginRateLimiter limiter;
    public AuthController(AuthService auth, LoginRateLimiter limiter) {
        this.auth = auth;
        this.limiter = limiter;
    }
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        limiter.acquire(request.getRemoteAddr(), body.getUsername());
        return ApiResponse.success(auth.login(body));
    }
}
