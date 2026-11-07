package cn.paperhub.paper.controller;

import cn.paperhub.audit.service.AuditService;
import cn.paperhub.common.api.ApiResponse;
import cn.paperhub.paper.dto.*;
import cn.paperhub.paper.service.PaperService;
import cn.paperhub.paper.vo.*;
import cn.paperhub.security.SessionPrincipal;
import java.io.*;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/papers")
public class PaperController {
    private static final Logger LOG=LoggerFactory.getLogger(PaperController.class);
    private final PaperService papers;
    private final AuditService audit;
    public PaperController(PaperService papers, AuditService audit) { this.papers=papers; this.audit=audit; }
    @GetMapping("/mine")
    public ApiResponse<?> mine(@AuthenticationPrincipal SessionPrincipal actor) { return ApiResponse.success(papers.mine(actor)); }
    @GetMapping("/{id}")
    public ApiResponse<PaperView> detail(@PathVariable long id, @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(papers.detail(id,actor));
    }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PaperView> upload(@RequestPart("payload") UploadRequest payload, @RequestPart("file") MultipartFile file,
                                        @AuthenticationPrincipal SessionPrincipal actor) throws IOException {
        return ApiResponse.success(papers.upload(payload,file,actor));
    }
    @PatchMapping("/{id}")
    public ApiResponse<PaperView> edit(@PathVariable long id, @RequestBody PaperEditRequest patch,
                                      @AuthenticationPrincipal SessionPrincipal actor) {
        return ApiResponse.success(papers.edit(id,patch,actor));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable long id, @AuthenticationPrincipal SessionPrincipal actor) throws IOException {
        return ApiResponse.success(papers.delete(id,actor));
    }
    @GetMapping("/{id}/file")
    public void preview(@PathVariable long id, @AuthenticationPrincipal SessionPrincipal actor,
                        HttpServletResponse response) throws IOException { stream(id,false,actor,response); }
    @PostMapping("/{id}/download")
    public void download(@PathVariable long id, @AuthenticationPrincipal SessionPrincipal actor,
                         HttpServletResponse response) throws IOException { stream(id,true,actor,response); }
    private void stream(long id, boolean download, SessionPrincipal actor, HttpServletResponse response) throws IOException {
        try (PaperFile file=papers.file(id,download,actor)) {
            String filename=file.title.replaceAll("[\\x00-\\x1f\\x7f\\\\/]", "_")+".pdf";
            response.setContentType(MediaType.APPLICATION_PDF_VALUE); response.setContentLengthLong(file.size);
            response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
            response.setHeader("X-Content-Type-Options","nosniff");
            response.setHeader("Content-Security-Policy","sandbox");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.builder(download?"attachment":"inline").filename(filename,StandardCharsets.UTF_8).build().toString());
            try { StreamUtils.copy(file.input,response.getOutputStream()); response.flushBuffer(); }
            catch (IOException ex) {
                if(file.sensitive) {
                    try { audit.sensitive(actor,file.id,file.ownerId,"fileTransferFailed",false); }
                    catch (RuntimeException auditFailure) { LOG.error("Cannot persist transfer failure; paperId={}",id); }
                }
                if(!response.isCommitted()) { response.reset(); throw ex; }
                LOG.info("File transfer interrupted; paperId={}",id);
            }
        }
    }
}

