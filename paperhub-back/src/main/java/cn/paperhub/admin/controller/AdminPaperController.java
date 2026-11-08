package cn.paperhub.admin.controller;

import cn.paperhub.admin.dto.*;
import cn.paperhub.admin.service.*;
import cn.paperhub.common.api.*;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.paper.service.*;
import cn.paperhub.paper.vo.PaperView;
import cn.paperhub.security.SessionPrincipal;
import java.io.IOException;
import javax.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminPaperController {
    private final SharedSearchService search;
    private final PaperService papers;
    private final AdminReviewService review;
    private final DuplicateResolutionService duplicates;
    private final AdminHistoryService history;
    public AdminPaperController(SharedSearchService search,PaperService papers,AdminReviewService review,
            DuplicateResolutionService duplicates,AdminHistoryService history) {
        this.search=search; this.papers=papers; this.review=review; this.duplicates=duplicates; this.history=history;
    }
    @GetMapping("/papers")
    public ApiResponse<?> list(@Valid @ModelAttribute AdminPaperQuery input,@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(search.admin(input,actor));
    }
    @GetMapping("/papers/{id}")
    public ApiResponse<PaperView> detail(@PathVariable long id,@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(papers.detail(id,actor));
    }
    @PatchMapping("/papers/{id}/offline")
    public ApiResponse<Boolean> offline(@PathVariable long id,@RequestBody(required=false) EmptyRequest input,
                                        @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(review.offline(id,actor));
    }
    @PatchMapping("/papers/{id}/restore")
    public ApiResponse<Boolean> restore(@PathVariable long id,@RequestBody(required=false) EmptyRequest input,
                                        @AuthenticationPrincipal SessionPrincipal actor) {
        return approved(review.restore(id,actor));
    }
    @DeleteMapping("/papers/{id}")
    public ApiResponse<Boolean> delete(@PathVariable long id,@AuthenticationPrincipal SessionPrincipal actor) throws IOException {
        return ApiResponse.success(papers.delete(id,actor));
    }
    @GetMapping("/review/papers")
    public ApiResponse<?> group(@AuthenticationPrincipal SessionPrincipal actor) { return ApiResponse.success(review.group(actor)); }
    @PostMapping("/review/papers/{id}/approve")
    public ApiResponse<Boolean> approve(@PathVariable long id,@Valid @RequestBody ReviewRequest input,
                                        @AuthenticationPrincipal SessionPrincipal actor) {
        return approved(review.approve(id,input.getComment(),input.getCopyrightConfirmed(),actor));
    }
    @PostMapping("/review/papers/{id}/reject")
    public ApiResponse<Boolean> reject(@PathVariable long id,@Valid @RequestBody RejectRequest input,
                                       @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(review.reject(id,input.getComment(),actor));
    }
    @GetMapping("/review/records")
    public ApiResponse<?> reviews(@RequestParam(defaultValue="all") String action) { return ApiResponse.success(history.reviews(action)); }
    @GetMapping("/duplicates")
    public ApiResponse<?> pairs(@RequestParam(defaultValue="all") String status,@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(history.pairs(status,actor));
    }
    @GetMapping("/duplicate-records")
    public ApiResponse<?> duplicateRecords(@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(history.duplicateRecords(actor));
    }
    @PostMapping("/duplicates/{id}/confirm")
    public ApiResponse<Boolean> confirm(@PathVariable long id,@Valid @RequestBody DuplicateActionRequest input,
                                        @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(duplicates.resolve(id,"duplicate",input.getNote(),actor));
    }
    @PostMapping("/duplicates/{id}/distinct")
    public ApiResponse<Boolean> distinct(@PathVariable long id,@Valid @RequestBody DuplicateActionRequest input,
                                         @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(duplicates.resolve(id,"distinct",input.getNote(),actor));
    }
    @PostMapping("/duplicates/{id}/link")
    public ApiResponse<Boolean> link(@PathVariable long id,@Valid @RequestBody DuplicateActionRequest input,
                                     @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(duplicates.resolve(id,"linked",input.getNote(),actor));
    }
    private ApiResponse<Boolean> approved(boolean result) {
        if(!result) throw new BusinessException(ErrorCode.BIZ_REJECTED,"请先处理待定的疑似重复论文");
        return ApiResponse.success(true);
    }
}

