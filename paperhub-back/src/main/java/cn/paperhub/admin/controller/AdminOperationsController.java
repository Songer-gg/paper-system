package cn.paperhub.admin.controller;

import cn.paperhub.admin.dto.ReportResolveRequest;
import cn.paperhub.admin.service.*;
import cn.paperhub.common.api.ApiResponse;
import cn.paperhub.security.SessionPrincipal;
import javax.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminOperationsController {
    private final ReportService reports;
    private final AdminOperationsService operations;
    public AdminOperationsController(ReportService reports,AdminOperationsService operations) { this.reports=reports;this.operations=operations; }
    @GetMapping("/reports") public ApiResponse<?> reports(@RequestParam(defaultValue="all") String status,
                                                         @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(reports.list(status,false,actor));
    }
    @GetMapping("/report-records") public ApiResponse<?> history(@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(reports.list("resolved",true,actor));
    }
    @PostMapping("/reports/{id}/resolve") public ApiResponse<?> resolve(@PathVariable long id,@Valid @RequestBody ReportResolveRequest input,
                                                                      @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(reports.resolve(id,input.getResult(),input.getOfflinePaperToo(),actor));
    }
    @GetMapping("/logs") public ApiResponse<?> logs(@RequestParam(defaultValue="all") String action,
                                                   @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(operations.logs(action,actor));
    }
    @GetMapping("/settings") public ApiResponse<?> settings(@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(operations.settings(actor));
    }
}
