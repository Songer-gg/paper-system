package cn.paperhub.paper.service;

import cn.paperhub.audit.service.AuditService;
import cn.paperhub.auth.mapper.UserAccountMapper;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.common.storage.*;
import cn.paperhub.paper.dto.*;
import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.mapper.PaperMapper;
import cn.paperhub.paper.vo.*;
import cn.paperhub.security.SessionPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.io.*;
import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PaperService {
    private final PaperMapper mapper;
    private final UserAccountMapper users;
    private final PaperValidation validation;
    private final LocalFileStorage storage;
    private final FileLifecycle files;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public PaperService(PaperMapper mapper, UserAccountMapper users, PaperValidation validation,
                        LocalFileStorage storage, FileLifecycle files, AuditService audit, JdbcTemplate jdbc, Clock clock) {
        this.mapper=mapper; this.users=users; this.validation=validation; this.storage=storage;
        this.files=files; this.audit=audit; this.jdbc=jdbc; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public Map<String, List<PaperView>> mine(SessionPrincipal actor) {
        Map<String, List<PaperView>> result = new LinkedHashMap<>();
        result.put("privatePapers", new ArrayList<>()); result.put("sharedPapers", new ArrayList<>());
        String name = users.selectById(actor.getId()).getName();
        List<PaperEntity> papers = mapper.selectList(new QueryWrapper<PaperEntity>().eq("uploader_id", actor.getId())
                .isNull("deleted_at").orderByDesc("uploaded_at", "id"));
        Map<Long, List<Long>> tags = new HashMap<>();
        if (!papers.isEmpty()) {
            // Only the token subject is used; clients cannot select another user's library.
            jdbc.query("SELECT pt.paper_id,pt.tag_id FROM paper_tag pt JOIN paper p ON p.id=pt.paper_id " +
                    "WHERE p.uploader_id=? AND p.deleted_at IS NULL ORDER BY pt.tag_id",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                            tags.computeIfAbsent(rs.getLong(1), ignored -> new ArrayList<>()).add(rs.getLong(2)), actor.getId());
        }
        for (PaperEntity paper : papers) result.get(paper.getVisibility()+"Papers")
                .add(view(paper, actor, name, tags.getOrDefault(paper.getId(), Collections.emptyList())));
        if ("admin".equals(actor.getRole())) {
            for (PaperView paper : result.get("privatePapers"))
                audit.sensitive(actor, paper.getId(), actor.getId(), "listOwnPrivateMetadata", true);
        }
        return result;
    }
    @Transactional(readOnly=true)
    public PaperView detail(long id, SessionPrincipal actor) {
        PaperEntity paper = readable(id, actor, false);
        sensitive(actor, paper, "metadata", true);
        return view(paper, actor);
    }
    @Transactional(timeout=60, rollbackFor=Exception.class)
    public PaperView upload(UploadRequest input, MultipartFile file, SessionPrincipal actor) throws IOException {
        if (!"private".equals(input.getVisibility()) && !"shared".equals(input.getVisibility())) PaperValidation.invalid();
        if (input.getCopyrightAgreed() == null || input.getCategoryId() == null || input.getTagIds() == null
                || input.getKeywords() == null) PaperValidation.invalid();
        boolean shared = "shared".equals(input.getVisibility());
        if (shared && !Boolean.TRUE.equals(input.getCopyrightAgreed()))
            throw new BusinessException(ErrorCode.BIZ_REJECTED, "共享上传必须确认版权授权");
        PaperEntity paper = new PaperEntity();
        apply(paper, input); paper.setVisibility(input.getVisibility()); paper.setUploaderId(actor.getId());
        validation.validate(paper, input.getTagIds()); classify(paper, input.getTagIds());
        LocalFileStorage.Stored stored = storage.pdf(file);
        files.created(stored);
        paper.setFilePath(stored.key); paper.setFileSha256(stored.sha256); paper.setPageCount(stored.pages);
        paper.setFileSizeMb(BigDecimal.valueOf(stored.bytes).divide(BigDecimal.valueOf(1024L*1024), 2, RoundingMode.HALF_UP));
        paper.setUploadedAt(LocalDateTime.now(clock)); paper.setDownloads(0L); paper.setVersion(0L);
        paper.setReviewStatus(shared ? "pending" : null);
        paper.setCopyrightAgreed(input.getCopyrightAgreed());
        paper.setCopyrightAgreedAt(input.getCopyrightAgreed() ? LocalDateTime.now(clock) : null);
        mapper.insert(paper); PaperValidation.id(paper.getId());
        replaceTags(paper.getId(), input.getTagIds());
        if (shared) suspects(paper);
        audit.mutation(actor, "论文上传", target(paper));
        sensitive(actor, paper, "uploadedMetadata", true);
        return view(paper, actor);
    }
    @Transactional(timeout=60, rollbackFor=Exception.class)
    public PaperView edit(long id, PaperEditRequest input, SessionPrincipal actor) {
        PaperEntity paper = writable(id, actor);
        if (input.isEmpty()) PaperValidation.invalid();
        List<Long> tags = input.supplied("tagIds") ? input.getTagIds() : mapper.tagIds(id);
        List<Object> before = signature(paper, mapper.tagIds(id));
        apply(paper, input); validation.validate(paper, tags); classify(paper, tags);
        if (!before.equals(signature(paper, tags))) {
            if ("approved".equals(paper.getReviewStatus())) {
                paper.setReviewStatus("pending"); paper.setReviewComment(null); paper.setOfflineReason(null);
            }
            long version = paper.getVersion(); paper.setVersion(version+1);
            if (mapper.update(paper, new UpdateWrapper<PaperEntity>().eq("id", id)
                    .eq("version", version).isNull("deleted_at")) != 1) throw new BusinessException(ErrorCode.CONFLICT);
            replaceTags(id, tags);
            if ("shared".equals(paper.getVisibility())) suspects(paper);
            audit.mutation(actor, "论文编辑", target(paper));
        }
        sensitive(actor, paper, "editedMetadata", true);
        return view(paper, actor);
    }
    @Transactional(timeout=60, rollbackFor=Exception.class)
    public boolean delete(long id, SessionPrincipal actor) throws IOException {
        PaperEntity paper = writable(id, actor);
        files.deleting(paper.getFilePath());
        if (mapper.softDelete(id, paper.getVersion(), LocalDateTime.now(clock)) != 1)
            throw new BusinessException(ErrorCode.CONFLICT);
        audit.mutation(actor, "论文删除", target(paper));
        return true;
    }
    @Transactional(timeout=60, rollbackFor=Exception.class)
    public PaperFile file(long id, boolean download, SessionPrincipal actor) throws IOException {
        PaperEntity paper = readable(id, actor, true);
        InputStream input;
        long size;
        try {
            size=Files.size(storage.resolve(paper.getFilePath()));
            input=storage.open(paper.getFilePath());
        } catch (IOException ex) {
            sensitive(actor, paper, download ? "downloadMissingFile" : "previewMissingFile", false);
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        boolean transferred = false;
        try {
            sensitive(actor, paper, download ? "downloadAuthorized" : "previewAuthorized", true);
            if (download && mapper.incrementDownloads(id) != 1) throw new BusinessException(ErrorCode.CONFLICT);
            transferred=true;
            return new PaperFile(input, size, paper.getTitle(), id, paper.getUploaderId(), isSensitive(actor,paper));
        } finally { if (!transferred) input.close(); }
    }
    private PaperEntity readable(long id, SessionPrincipal actor, boolean lock) {
        PaperValidation.id(id);
        if (lock && mapper.lockActive(id) == null) {
            PaperEntity missing = mapper.selectById(id);
            if (missing != null) sensitive(actor, missing, "deletedFileAccessDenied", false);
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        PaperEntity paper = mapper.selectById(id);
        if (paper == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        if (paper.getDeletedAt() != null) {
            sensitive(actor, paper, "deletedAccessDenied", false);
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!paper.getUploaderId().equals(actor.getId()) && !"admin".equals(actor.getRole())
                && !("shared".equals(paper.getVisibility()) && "approved".equals(paper.getReviewStatus())))
            throw new BusinessException(ErrorCode.NOT_FOUND);
        return paper;
    }
    private PaperEntity writable(long id, SessionPrincipal actor) {
        PaperEntity paper=readable(id, actor, true);
        if (!paper.getUploaderId().equals(actor.getId()) && !"admin".equals(actor.getRole()))
            throw new BusinessException(ErrorCode.FORBIDDEN);
        return paper;
    }
    private boolean isSensitive(SessionPrincipal actor, PaperEntity paper) {
        return "admin".equals(actor.getRole()) && "private".equals(paper.getVisibility());
    }
    private void sensitive(SessionPrincipal actor, PaperEntity paper, String action, boolean success) {
        if (isSensitive(actor, paper)) audit.sensitive(actor, paper.getId(), paper.getUploaderId(), action, success);
    }
    private String target(PaperEntity paper) { return "paperId="+paper.getId()+";ownerId="+paper.getUploaderId(); }
    private void classify(PaperEntity paper, List<Long> tags) {
        if (paper.getCategoryId() != null) checkClassification("category", paper.getCategoryId(), paper);
        List<Long> sorted = new ArrayList<>(tags); Collections.sort(sorted);
        for (Long id : sorted) checkClassification("tag", id, paper);
    }
    private void checkClassification(String table, long id, PaperEntity paper) {
        // Table name is an internal constant, never supplied by the client.
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT owner_id,custom FROM "+table+" WHERE id=? FOR SHARE",id);
        if (rows.isEmpty()) PaperValidation.invalid();
        Object owner = rows.get(0).get("owner_id");
        if (owner != null && ("shared".equals(paper.getVisibility()) || ((Number)owner).longValue()!=paper.getUploaderId()))
            PaperValidation.invalid();
    }
    private void replaceTags(long id, List<Long> tags) {
        mapper.deleteTags(id);
        for (Long tag : tags) mapper.insertTag(id, tag);
    }
    private void apply(PaperEntity paper, PaperEditRequest input) {
        if(input.supplied("title")) paper.setTitle(input.getTitle());
        if(input.supplied("authors")) paper.setAuthors(input.getAuthors());
        if(input.supplied("abstractText")) paper.setAbstractText(input.getAbstractText());
        if(input.supplied("doi")) { if(input.getDoi()==null) PaperValidation.invalid(); paper.setDoi(input.getDoi()); }
        if(input.supplied("journal")) { if(input.getJournal()==null) PaperValidation.invalid(); paper.setJournal(input.getJournal()); }
        if(input.supplied("year")) paper.setYear(input.getYear());
        if(input.supplied("keywords")) paper.setKeywords(input.getKeywords());
        if(input.supplied("categoryId")) paper.setCategoryId(input.getCategoryId());
        if(input.supplied("link")) { if(input.getLink()==null) PaperValidation.invalid(); paper.setLink(input.getLink()); }
    }
    private List<Object> signature(PaperEntity p, List<Long> tags) {
        List<Long> sorted = tags == null ? Collections.emptyList() : new ArrayList<>(tags);
        Collections.sort(sorted);
        return Arrays.asList(p.getTitle(),p.getAuthors(),p.getAbstractText(),p.getDoi(),p.getJournal(),
                p.getYear(),p.getKeywords(),p.getCategoryId(),sorted,p.getLink());
    }
    private void suspects(PaperEntity paper) {
        List<PaperEntity> candidates = mapper.selectList(new QueryWrapper<PaperEntity>()
                .eq("visibility","shared").isNull("deleted_at").ne("review_status","rejected")
                .ne("id",paper.getId()).orderByAsc("id"));
        for (PaperEntity candidate : candidates) {
            double similarity = titleSimilarity(candidate.getTitle(), paper.getTitle());
            if (similarity >= 0.80) {
                PaperEntity original = candidate.getId() < paper.getId() ? candidate : paper;
                PaperEntity newer = original == paper ? candidate : paper;
                mapper.suspect(original, newer, LocalDateTime.now(clock),
                        BigDecimal.valueOf(similarity*100).setScale(2,RoundingMode.HALF_UP));
            }
        }
    }
    static double titleSimilarity(String one, String two) {
        String a=one.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]","");
        String b=two.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]","");
        if(a.equals(b)) return 1;
        Set<String> left=grams(a), right=grams(b), union=new HashSet<>(left);
        union.addAll(right); left.retainAll(right);
        return union.isEmpty() ? 0 : (double)left.size()/union.size();
    }
    private static Set<String> grams(String value) {
        Set<String> result=new HashSet<>();
        if(value.length()<2) result.add(value);
        for(int i=0;i<value.length()-1;i++) result.add(value.substring(i,i+2));
        return result;
    }
    private PaperView view(PaperEntity paper, SessionPrincipal actor) {
        return view(paper,actor,users.selectById(paper.getUploaderId()).getName(),mapper.tagIds(paper.getId()));
    }
    private PaperView view(PaperEntity p, SessionPrincipal actor, String name, List<Long> tags) {
        PaperView v=new PaperView();
        v.setId(p.getId()); v.setTitle(p.getTitle()); v.setAuthors(p.getAuthors()); v.setAbstractText(p.getAbstractText());
        v.setDoi(p.getDoi()==null?"":p.getDoi()); v.setJournal(p.getJournal()==null?"":p.getJournal());
        v.setYear(p.getYear()); v.setKeywords(p.getKeywords()); v.setCategoryId(p.getCategoryId()); v.setTagIds(tags);
        v.setVisibility(p.getVisibility()); v.setUploaderId(p.getUploaderId()); v.setUploaderName(name);
        v.setUploadedAt(p.getUploadedAt()); v.setDownloads(p.getDownloads()); v.setFileSizeMB(p.getFileSizeMb());
        v.setPageCount(p.getPageCount()); v.setReviewStatus(p.getReviewStatus()); v.setOfflineReason(p.getOfflineReason());
        if(p.getUploaderId().equals(actor.getId()) || "admin".equals(actor.getRole())) v.setReviewComment(p.getReviewComment());
        v.setLink(p.getLink()); return v;
    }
}

