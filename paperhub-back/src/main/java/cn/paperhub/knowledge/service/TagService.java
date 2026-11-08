package cn.paperhub.knowledge.service;

import cn.paperhub.audit.service.AuditService;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.knowledge.entity.TagEntity;
import cn.paperhub.knowledge.mapper.*;
import cn.paperhub.knowledge.vo.TagView;
import cn.paperhub.paper.service.*;
import cn.paperhub.paper.vo.PaperView;
import cn.paperhub.security.SessionPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {
    private final TagMapper mapper;
    private final KnowledgeRelationMapper relations;
    private final KnowledgeCounts counts;
    private final PaperListingService papers;
    private final AuditService audit;
    private final Clock clock;
    public TagService(TagMapper mapper, KnowledgeRelationMapper relations, KnowledgeCounts counts,
                      PaperListingService papers, AuditService audit, Clock clock) {
        this.mapper=mapper; this.relations=relations; this.counts=counts;
        this.papers=papers; this.audit=audit; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public List<TagView> list(SessionPrincipal actor) {
        QueryWrapper<TagEntity> query=new QueryWrapper<>();
        if(!KnowledgeAccess.admin(actor)) query.and(scope->scope.isNull("owner_id").or().eq("owner_id",actor.getId()));
        List<TagEntity> tags=mapper.selectList(query.orderByAsc("id"));
        auditTags(tags,actor,"tagList");
        Map<Long,Long> totals=counts.tags(actor);
        List<TagView> result=new ArrayList<>();
        for(TagEntity tag:tags) result.add(new TagView(tag,totals.getOrDefault(tag.getId(),0L)));
        return result;
    }
    @Transactional(timeout=60)
    public TagView create(String name, String color, SessionPrincipal actor) {
        name=KnowledgeAccess.name(name); color=color(color);
        TagEntity tag=new TagEntity();
        tag.setName(name); tag.setColor(color); tag.setCreatedAt(LocalDateTime.now(clock));
        tag.setOwnerId(KnowledgeAccess.admin(actor)?null:actor.getId()); tag.setCustom(tag.getOwnerId()!=null);
        mapper.insert(tag); PaperValidation.id(tag.getId());
        audit.mutation(actor,"标签创建",target(tag));
        return new TagView(tag,0);
    }
    @Transactional(timeout=60)
    public boolean edit(long id, String name, String color, SessionPrincipal actor) {
        PaperValidation.id(id); name=KnowledgeAccess.name(name); color=color(color);
        TagEntity tag=readable(id,actor,true); KnowledgeAccess.writable(actor,tag.getOwnerId());
        auditTags(Collections.singletonList(tag),actor,"tagEditAccess");
        if(!name.equals(tag.getName()) || !color.equals(tag.getColor())) {
            if(mapper.edit(id,name,color)!=1) conflict();
            audit.mutation(actor,"标签修改",target(tag));
        }
        return true;
    }
    @Transactional(timeout=60)
    public boolean delete(long id, SessionPrincipal actor) {
        PaperValidation.id(id);
        TagEntity tag=readable(id,actor,true); KnowledgeAccess.writable(actor,tag.getOwnerId());
        auditTags(Collections.singletonList(tag),actor,"tagDeleteAccess");
        List<Long> affected=relations.papersWithTag(id);
        for(Long paperId:affected) {
            relations.lockPaper(paperId);
            if(relations.bumpVersion(paperId)!=1) conflict();
        }
        relations.clearTag(id);
        if(mapper.deleteById(id)!=1) conflict();
        audit.mutation(actor,"标签删除",target(tag)+";paperCount="+affected.size());
        return true;
    }
    @Transactional(readOnly=true)
    public List<PaperView> papers(long id, SessionPrincipal actor) {
        PaperValidation.id(id);
        TagEntity tag=readable(id,actor,false);
        auditTags(Collections.singletonList(tag),actor,"tagPaperListAccess");
        return papers.tag(id,actor);
    }
    private TagEntity readable(long id, SessionPrincipal actor, boolean lock) {
        QueryWrapper<TagEntity> query=new QueryWrapper<TagEntity>().eq("id",id);
        if(lock) query.last("FOR UPDATE");
        TagEntity tag=mapper.selectOne(query);
        if(tag==null) throw new BusinessException(ErrorCode.NOT_FOUND);
        KnowledgeAccess.readable(actor,tag.getOwnerId()); return tag;
    }
    private void auditTags(List<TagEntity> tags, SessionPrincipal actor, String action) {
        if(!KnowledgeAccess.admin(actor)) return;
        Map<Long,Long> owners=new LinkedHashMap<>();
        for(TagEntity tag:tags) if(tag.getOwnerId()!=null) owners.put(tag.getId(),tag.getOwnerId());
        if(!owners.isEmpty()) audit.sensitiveResources(actor,"tag",owners,action,true);
    }
    private String color(String color) {
        if(color==null || !color.matches("#[0-9a-fA-F]{6}")) throw new BusinessException(ErrorCode.INVALID_PARAM);
        return color.toUpperCase(Locale.ROOT);
    }
    private String target(TagEntity tag) { return "tagId="+tag.getId()+";ownerId="+tag.getOwnerId(); }
    private void conflict() { throw new BusinessException(ErrorCode.CONFLICT); }
}

