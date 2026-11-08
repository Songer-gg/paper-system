package cn.paperhub.knowledge.controller;

import cn.paperhub.common.api.ApiResponse;
import cn.paperhub.knowledge.dto.*;
import cn.paperhub.knowledge.service.CategoryService;
import cn.paperhub.knowledge.vo.CategoryView;
import cn.paperhub.paper.vo.PaperView;
import cn.paperhub.security.SessionPrincipal;
import java.util.List;
import javax.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryService categories;
    public CategoryController(CategoryService categories) { this.categories=categories; }
    @GetMapping
    public ApiResponse<List<CategoryView>> list(@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(categories.list(actor));
    }
    @PostMapping
    public ApiResponse<CategoryView> create(@Valid @RequestBody CategoryCreateRequest input,
                                           @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(categories.create(input.getName(),input.getParentId(),actor));
    }
    @PatchMapping("/{id}")
    public ApiResponse<Boolean> rename(@PathVariable long id, @Valid @RequestBody CategoryRenameRequest input,
                                       @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(categories.rename(id,input.getName(),actor));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<List<String>> delete(@PathVariable long id, @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(categories.delete(id,actor));
    }
    @PostMapping("/{id}/move")
    public ApiResponse<Boolean> move(@PathVariable long id, @RequestBody CategoryMoveRequest input,
                                     @AuthenticationPrincipal SessionPrincipal actor) {
        input.requireParent();
        return ApiResponse.success(categories.move(id,input.getParentId(),actor));
    }
    @GetMapping("/{id}/papers")
    public ApiResponse<List<PaperView>> papers(@PathVariable long id, @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(categories.papers(id,actor));
    }
}

