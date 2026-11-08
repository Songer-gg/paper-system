package cn.paperhub.statistics.controller;

import cn.paperhub.common.api.ApiResponse;
import cn.paperhub.security.SessionPrincipal;
import cn.paperhub.statistics.service.StatisticsService;
import cn.paperhub.statistics.vo.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class StatisticsController {
    private final StatisticsService statistics;
    public StatisticsController(StatisticsService statistics) { this.statistics=statistics; }
    @GetMapping("/me/dashboard") public ApiResponse<UserDashboardView> dashboard(@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(statistics.dashboard(actor));
    }
    @GetMapping("/me/statistics") public ApiResponse<UserStatisticsView> statistics(@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(statistics.statistics(actor));
    }
    @GetMapping("/admin/dashboard") public ApiResponse<AdminDashboardView> admin(@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(statistics.admin(actor));
    }
}
