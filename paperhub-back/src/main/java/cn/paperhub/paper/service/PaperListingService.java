package cn.paperhub.paper.service;

import cn.paperhub.audit.service.AuditService;
import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.mapper.PaperMapper;
import cn.paperhub.paper.vo.PaperView;
import cn.paperhub.security.SessionPrincipal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaperListingService {
    private final PaperMapper mapper;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public PaperListingService(PaperMapper mapper, JdbcTemplate jdbc, AuditService audit) {
        this.mapper=mapper; this.jdbc=jdbc; this.audit=audit;
    }
    @Transactional(readOnly=true)
    public List<PaperView> categories(List<Long> categoryIds, SessionPrincipal actor) {
        if(categoryIds.isEmpty()) return Collections.emptyList();
        return views(mapper.selectList(PaperVisibility.query(actor).in("category_id",categoryIds)
                .orderByDesc("uploaded_at","id")),actor,"categoryPaperList");
    }
    @Transactional(readOnly=true)
    public List<PaperView> tag(long tagId, SessionPrincipal actor) {
        return views(mapper.selectList(PaperVisibility.query(actor)
                .apply("EXISTS (SELECT 1 FROM paper_tag pt WHERE pt.paper_id=paper.id AND pt.tag_id={0})",tagId)
                .orderByDesc("uploaded_at","id")),actor,"tagPaperList");
    }
    private List<PaperView> views(List<PaperEntity> papers, SessionPrincipal actor, String action) {
        Map<Long,Long> privateOwners=new LinkedHashMap<>();
        Set<Long> uploaders=new LinkedHashSet<>();
        List<Long> ids=new ArrayList<>();
        for(PaperEntity paper:papers) {
            ids.add(paper.getId()); uploaders.add(paper.getUploaderId());
            if("admin".equals(actor.getRole()) && "private".equals(paper.getVisibility()))
                privateOwners.put(paper.getId(),paper.getUploaderId());
        }
        if(!privateOwners.isEmpty()) audit.sensitiveResources(actor,"paper",privateOwners,action,true);
        Map<Long,String> names=new HashMap<>();
        Map<Long,List<Long>> tags=new HashMap<>();
        List<Long> owners=new ArrayList<>(uploaders);
        for(int start=0; start<owners.size(); start+=500) {
            List<Long> batch=owners.subList(start,Math.min(start+500,owners.size()));
            jdbc.query("SELECT id,name FROM `user` WHERE id IN ("+marks(batch.size())+")",
                    (RowCallbackHandler)rs->names.put(rs.getLong(1),rs.getString(2)),batch.toArray());
        }
        for(int start=0; start<ids.size(); start+=500) {
            List<Long> batch=ids.subList(start,Math.min(start+500,ids.size()));
            jdbc.query("SELECT paper_id,tag_id FROM paper_tag WHERE paper_id IN ("+marks(batch.size())+") ORDER BY tag_id",
                    (RowCallbackHandler)rs->tags.computeIfAbsent(rs.getLong(1),ignored->new ArrayList<>()).add(rs.getLong(2)),
                    batch.toArray());
        }
        List<PaperView> result=new ArrayList<>();
        for(PaperEntity paper:papers) result.add(PaperViewFactory.create(paper,actor,
                names.get(paper.getUploaderId()),tags.getOrDefault(paper.getId(),Collections.emptyList())));
        return result;
    }
    private String marks(int size) { return String.join(",",Collections.nCopies(size,"?")); }
}

