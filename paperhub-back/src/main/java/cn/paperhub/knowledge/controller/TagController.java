package cn.paperhub.knowledge.controller;

import cn.paperhub.common.api.ApiResponse;
import cn.paperhub.knowledge.dto.TagRequest;
import cn.paperhub.knowledge.service.TagService;
import cn.paperhub.knowledge.vo.TagView;
import cn.paperhub.paper.vo.PaperView;
import cn.paperhub.security.SessionPrincipal;
import java.util.List;
import javax.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {
    private final TagService tags;
    public TagController(TagService tags) { this.tags=tags; }
    @GetMapping
    public ApiResponse<List<TagView>> list(@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(tags.list(actor));
    }
    @PostMapping
    public ApiResponse<TagView> create(@Valid @RequestBody TagRequest input, @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(tags.create(input.getName(),input.getColor(),actor));
    }
    @PatchMapping("/{id}")
    public ApiResponse<Boolean> edit(@PathVariable long id, @Valid @RequestBody TagRequest input,
                                     @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(tags.edit(id,input.getName(),input.getColor(),actor));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable long id, @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(tags.delete(id,actor));
    }
    @GetMapping("/{id}/papers")
    public ApiResponse<List<PaperView>> papers(@PathVariable long id, @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(tags.papers(id,actor));
    }
}

