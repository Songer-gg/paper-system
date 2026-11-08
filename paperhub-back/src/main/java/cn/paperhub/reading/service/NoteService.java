package cn.paperhub.reading.service;

import cn.paperhub.audit.service.AuditService;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.service.*;
import cn.paperhub.reading.dto.*;
import cn.paperhub.reading.entity.NoteEntity;
import cn.paperhub.reading.mapper.NoteMapper;
import cn.paperhub.reading.vo.NoteView;
import cn.paperhub.security.SessionPrincipal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteService {
    private final NoteMapper notes;
    private final NoteValidation validation;
    private final PersonalPaperAccess access;
    private final AuditService audit;
    private final Clock clock;
    public NoteService(NoteMapper notes,NoteValidation validation,PersonalPaperAccess access,AuditService audit,Clock clock) {
        this.notes=notes; this.validation=validation; this.access=access; this.audit=audit; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public List<NoteView> list(NoteQuery query,SessionPrincipal actor) {
        if(query.getPaperId()!=null) PaperValidation.id(query.getPaperId());
        List<NoteEntity> rows=notes.owned(actor.getId(),"admin".equals(actor.getRole()),query.getPaperId(),
                validation.keyword(query.getKeyword()));
        if("admin".equals(actor.getRole())) {
            Map<Long,Long> owners=new LinkedHashMap<>();
            for(NoteEntity row:rows) if("private".equals(row.getSourceVisibility()) && row.getSourceOwnerId()!=null)
                owners.put(row.getPaperId(),row.getSourceOwnerId());
            if(!owners.isEmpty()) audit.sensitiveResources(actor,"paper",owners,"ownNoteSnapshots",true);
        }
        List<NoteView> result=new ArrayList<>();
        for(NoteEntity row:rows) result.add(new NoteView(row,Boolean.TRUE.equals(row.getPaperAvailable())));
        return result;
    }
    @Transactional(timeout=60)
    public NoteView create(NoteCreateRequest input,SessionPrincipal actor) {
        PaperValidation.id(input.getPaperId());
        String title=validation.title(input.getTitle()),content=validation.content(input.getContent());
        PaperEntity paper=access.visible(access.lock(input.getPaperId()),actor,"noteCreateSource");
        LocalDateTime now=LocalDateTime.now(clock).withNano(0);
        NoteEntity note=new NoteEntity();
        note.setUserId(actor.getId()); note.setPaperId(paper.getId()); note.setPaperTitle(paper.getTitle());
        note.setTitle(title); note.setContent(content); note.setCreatedAt(now); note.setUpdatedAt(now);
        notes.insert(note); PaperValidation.id(note.getId());
        audit.mutation(actor,"笔记创建",target(note));
        return new NoteView(note,true);
    }
    @Transactional(timeout=60)
    public NoteView edit(long id,NotePatchRequest patch,SessionPrincipal actor) {
        PaperValidation.id(id); if(patch.empty()) PaperValidation.invalid();
        if(patch.has("paperId")) PaperValidation.id(patch.getPaperId());
        NoteEntity reference=reference(id,actor);
        Map<Long,PaperEntity> sources=lockSources(reference.getPaperId(),patch.has("paperId")?patch.getPaperId():null);
        NoteEntity note=current(reference,actor);
        String title=validation.title(patch.has("title")?patch.getTitle():note.getTitle());
        String content=validation.content(patch.has("content")?patch.getContent():note.getContent());
        boolean relink=patch.has("paperId") && !patch.getPaperId().equals(note.getPaperId());
        PaperEntity source=sources.get(note.getPaperId());
        if(relink) {
            PaperEntity next=access.visible(sources.get(patch.getPaperId()),actor,"noteRelinkSource");
            access.sensitive(source,actor,"notePreviousSnapshot",true);
            note.setPaperId(next.getId()); note.setPaperTitle(next.getTitle()); source=next;
        } else access.sensitive(source,actor,"ownNoteSnapshotEdit",true);
        note.setTitle(title); note.setContent(content); note.setUpdatedAt(LocalDateTime.now(clock).withNano(0));
        if(notes.edit(note)!=1) throw new BusinessException(ErrorCode.CONFLICT);
        audit.mutation(actor,"笔记更新",target(note));
        return new NoteView(note,PaperVisibility.allowed(source,actor));
    }
    @Transactional(timeout=60)
    public boolean delete(long id,SessionPrincipal actor) {
        PaperValidation.id(id); NoteEntity reference=reference(id,actor);
        PaperEntity source=access.lock(reference.getPaperId()); NoteEntity note=current(reference,actor);
        access.sensitive(source,actor,"ownNoteDeleteAccess",true);
        if(notes.deleteOwned(id,actor.getId())!=1) throw new BusinessException(ErrorCode.CONFLICT);
        audit.mutation(actor,"笔记删除",target(note)); return true;
    }
    private NoteEntity reference(long id,SessionPrincipal actor) {
        NoteEntity reference=notes.reference(id,actor.getId());
        if(reference==null) throw new BusinessException(ErrorCode.NOT_FOUND);
        return reference;
    }
    private Map<Long,PaperEntity> lockSources(long previous,Long next) {
        Set<Long> ids=new TreeSet<>(); ids.add(previous); if(next!=null) ids.add(next);
        Map<Long,PaperEntity> result=new HashMap<>();
        for(Long id:ids) result.put(id,access.lock(id));
        return result;
    }
    private NoteEntity current(NoteEntity reference,SessionPrincipal actor) {
        NoteEntity note=notes.lockOwned(reference.getId(),actor.getId());
        if(note==null) throw new BusinessException(ErrorCode.NOT_FOUND);
        if(!note.getPaperId().equals(reference.getPaperId()))
            throw new BusinessException(ErrorCode.CONFLICT,"笔记关联已改变，请刷新后重试");
        return note;
    }
    private String target(NoteEntity note) {
        return "noteId="+note.getId()+";paperId="+note.getPaperId()+";ownerId="+note.getUserId();
    }
}

