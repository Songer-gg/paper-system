package cn.paperhub.profile.controller;

import cn.paperhub.common.api.ApiResponse;
import cn.paperhub.profile.dto.*;
import cn.paperhub.profile.service.ProfileService;
import cn.paperhub.profile.vo.*;
import cn.paperhub.security.SessionPrincipal;
import javax.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {
    private final ProfileService profiles;
    public ProfileController(ProfileService profiles) { this.profiles=profiles; }
    @GetMapping("/profile")
    public ApiResponse<MyProfileView> get(@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(profiles.get(actor));
    }
    @PatchMapping("/profile")
    public ApiResponse<MyProfileView> edit(@RequestBody ProfilePatch patch,@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(profiles.edit(patch,actor));
    }
    @PutMapping("/password")
    public ApiResponse<PasswordView> password(@Valid @RequestBody PasswordChange input,@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(profiles.password(input,actor));
    }
}
