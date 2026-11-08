package cn.paperhub.admin.controller;

import cn.paperhub.admin.dto.*;
import cn.paperhub.admin.service.*;
import cn.paperhub.common.api.ApiResponse;
import cn.paperhub.security.SessionPrincipal;
import javax.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdminUserService users;
    private final AdminPersonalDataService personal;
    public AdminUserController(AdminUserService users,AdminPersonalDataService personal) { this.users=users;this.personal=personal; }
    @GetMapping public ApiResponse<?> list(@Valid @ModelAttribute AdminUserQuery query,@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(users.list(query,actor));
    }
    @GetMapping("/{id}") public ApiResponse<?> detail(@PathVariable long id,@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(users.detail(id,actor));
    }
    @GetMapping("/{id}/papers") public ApiResponse<?> papers(@PathVariable long id,@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(personal.papers(id,actor));
    }
    @GetMapping("/{id}/favorites") public ApiResponse<?> favorites(@PathVariable long id,@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(personal.favorites(id,actor));
    }
    @GetMapping("/{id}/reading-records") public ApiResponse<?> readings(@PathVariable long id,@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(personal.readings(id,actor));
    }
    @GetMapping("/{id}/notes") public ApiResponse<?> notes(@PathVariable long id,@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(personal.notes(id,actor));
    }
    @PatchMapping("/{id}/status") public ApiResponse<?> status(@PathVariable long id,@Valid @RequestBody UserStatusRequest input,
                                                              @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(users.status(id,input.getStatus(),actor));
    }
    @PostMapping("/{id}/password-reset") public ApiResponse<?> reset(@PathVariable long id,
            @RequestBody(required=false) PasswordResetRequest input,@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(users.reset(id,actor));
    }
}
