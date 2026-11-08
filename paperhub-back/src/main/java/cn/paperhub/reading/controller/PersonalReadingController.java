package cn.paperhub.reading.controller;

import cn.paperhub.common.api.ApiResponse;
import cn.paperhub.reading.dto.*;
import cn.paperhub.reading.service.*;
import cn.paperhub.reading.vo.*;
import cn.paperhub.security.SessionPrincipal;
import java.util.List;
import javax.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PersonalReadingController {
    private final FavoriteService favorites;
    private final ReadingService readings;
    private final NoteService notes;
    public PersonalReadingController(FavoriteService favorites,ReadingService readings,NoteService notes) {
        this.favorites=favorites; this.readings=readings; this.notes=notes;
    }
    @GetMapping("/me/favorites")
    public ApiResponse<List<FavoriteView>> favorites(@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(favorites.list(actor));
    }
    @PostMapping("/papers/{id}/favorite")
    public ApiResponse<FavoriteState> favorite(@PathVariable long id,@RequestBody(required=false) FavoriteRequest input,
                                              @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(favorites.set(id,input==null?null:input.getFavorited(),actor));
    }
    @GetMapping("/me/reading-records")
    public ApiResponse<List<ReadingView>> readings(@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(readings.list(actor));
    }
    @PostMapping("/papers/{id}/reading")
    public ApiResponse<ReadingView> add(@PathVariable long id,@RequestBody(required=false) ReadingAddRequest input,
                                       @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(readings.change(id,null,actor));
    }
    @PatchMapping("/me/reading-records/{paperId}")
    public ApiResponse<ReadingView> status(@PathVariable long paperId,@Valid @RequestBody ReadingStatusRequest input,
                                          @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(readings.change(paperId,input.getStatus(),actor));
    }
    @GetMapping("/me/notes")
    public ApiResponse<List<NoteView>> notes(@Valid @ModelAttribute NoteQuery query,@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(notes.list(query,actor));
    }
    @PostMapping("/me/notes")
    public ApiResponse<NoteView> create(@Valid @RequestBody NoteCreateRequest input,@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(notes.create(input,actor));
    }
    @PatchMapping("/me/notes/{id}")
    public ApiResponse<NoteView> edit(@PathVariable long id,@RequestBody NotePatchRequest input,
                                     @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(notes.edit(id,input,actor));
    }
    @DeleteMapping("/me/notes/{id}")
    public ApiResponse<Boolean> delete(@PathVariable long id,@AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(notes.delete(id,actor));
    }
}

