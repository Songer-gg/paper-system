package cn.paperhub.paper.controller;

import cn.paperhub.common.api.ApiResponse;
import cn.paperhub.paper.dto.*;
import cn.paperhub.paper.service.*;
import cn.paperhub.paper.vo.*;
import cn.paperhub.security.SessionPrincipal;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SharedPaperController {
    private final SharedSearchService search;
    private final ReviewFlowService flow;
    private final DuplicateDetectionService duplicates;
    public SharedPaperController(SharedSearchService search,ReviewFlowService flow,DuplicateDetectionService duplicates) {
        this.search=search; this.flow=flow; this.duplicates=duplicates;
    }
    @PatchMapping("/papers/{id}/review-flow")
    public ApiResponse<PaperView> flow(@PathVariable long id,@Valid @RequestBody ReviewFlowRequest input,
                                      @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(flow.change(id,input,actor));
    }
    @PostMapping("/papers/duplicate-check")
    public ApiResponse<DuplicateCheckView> duplicate(@Valid @RequestBody DuplicateCheckRequest input,
                                                    @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(duplicates.check(input,actor));
    }
    @GetMapping("/shared-papers")
    public ApiResponse<PageView<PaperView>> search(@Valid @ModelAttribute SharedQuery input,
                                                   @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(search.search(input,actor));
    }
    @GetMapping("/shared-papers/filter-options")
    public ApiResponse<Map<String,Object>> options() { return ApiResponse.success(search.options()); }
    @GetMapping("/shared-papers/{id}")
    public ApiResponse<PaperView> detail(@PathVariable long id,@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(search.detail(id,actor));
    }
}

