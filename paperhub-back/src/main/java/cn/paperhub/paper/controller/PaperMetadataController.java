package cn.paperhub.paper.controller;

import cn.paperhub.common.api.ApiResponse;
import cn.paperhub.paper.service.PaperMetadataService;
import cn.paperhub.paper.vo.PaperMetadataView;
import cn.paperhub.paper.dto.MetadataLookupRequest;
import cn.paperhub.paper.service.DoiMetadataService;
import javax.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/papers/metadata")
public class PaperMetadataController {
    private final PaperMetadataService service;
    private final DoiMetadataService lookup;
    public PaperMetadataController(PaperMetadataService service,DoiMetadataService lookup) { this.service=service;this.lookup=lookup; }
    @PostMapping("/lookup")
    public ApiResponse<PaperMetadataView> lookup(@Valid @RequestBody MetadataLookupRequest input) {
        return ApiResponse.success(lookup.lookup(input.getDoi()));
    }
    @PostMapping(value="/parse",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PaperMetadataView> parse(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(service.parse(file));
    }
}
