package cn.paperhub.knowledge.service;

import cn.paperhub.audit.service.AuditService;
import cn.paperhub.paper.service.PaperVisibility;
import cn.paperhub.security.SessionPrincipal;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeCounts {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public KnowledgeCounts(JdbcTemplate jdbc, AuditService audit) { this.jdbc=jdbc; this.audit=audit; }

    public Map<Long,Long> categories(SessionPrincipal actor) {
        Map<Long,Long> counts=new HashMap<>();
        jdbc.query("SELECT p.category_id,COUNT(*) FROM paper p WHERE p.category_id IS NOT NULL AND "+
                PaperVisibility.sql(actor)+" GROUP BY p.category_id",
                (RowCallbackHandler)rs->counts.put(rs.getLong(1),rs.getLong(2)),PaperVisibility.parameters(actor));
        if(KnowledgeAccess.admin(actor)) auditPrivate(
                "SELECT p.id,p.uploader_id FROM paper p WHERE p.category_id IS NOT NULL "+
                "AND p.deleted_at IS NULL AND p.visibility='private'",actor,"categoryCounts");
        return counts;
    }
    public Map<Long,Long> tags(SessionPrincipal actor) {
        Map<Long,Long> counts=new HashMap<>();
        jdbc.query("SELECT pt.tag_id,COUNT(*) FROM paper_tag pt JOIN paper p ON p.id=pt.paper_id WHERE "+
                PaperVisibility.sql(actor)+" GROUP BY pt.tag_id",
                (RowCallbackHandler)rs->counts.put(rs.getLong(1),rs.getLong(2)),PaperVisibility.parameters(actor));
        if(KnowledgeAccess.admin(actor)) auditPrivate(
                "SELECT DISTINCT p.id,p.uploader_id FROM paper_tag pt JOIN paper p ON p.id=pt.paper_id "+
                "WHERE p.deleted_at IS NULL AND p.visibility='private'",actor,"tagCounts");
        return counts;
    }
    private void auditPrivate(String sql, SessionPrincipal actor, String action) {
        Map<Long,Long> owners=new LinkedHashMap<>();
        jdbc.query(sql,(RowCallbackHandler)rs->owners.put(rs.getLong(1),rs.getLong(2)));
        if(!owners.isEmpty()) audit.sensitiveResources(actor,"paper",owners,action,true);
    }
}

