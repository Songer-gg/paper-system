package cn.paperhub.profile.controller;

import cn.paperhub.common.api.ApiResponse;
import cn.paperhub.profile.service.AvatarService;
import cn.paperhub.profile.vo.MyProfileView;
import cn.paperhub.security.SessionPrincipal;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/me")
public class AvatarController {
    private final AvatarService avatars;
    public AvatarController(AvatarService avatars) { this.avatars=avatars; }
    @PutMapping(value="/avatar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MyProfileView> update(@RequestPart("file") MultipartFile file,
                                            @AuthenticationPrincipal SessionPrincipal actor) throws IOException {
        return ApiResponse.success(avatars.update(file,actor));
    }
}

