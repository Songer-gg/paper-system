package cn.paperhub.paper.service;

import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.security.SessionPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

public final class PaperVisibility {
    private PaperVisibility() {}
    public static QueryWrapper<PaperEntity> query(SessionPrincipal actor) {
        QueryWrapper<PaperEntity> query=new QueryWrapper<PaperEntity>().isNull("deleted_at");
        if(!"admin".equals(actor.getRole()))
            query.and(scope->scope.eq("uploader_id",actor.getId())
                    .or(shared->shared.eq("visibility","shared").eq("review_status","approved")));
        return query;
    }
    /** The fixed alias p is used only by internal aggregate queries. */
    public static String sql(SessionPrincipal actor) {
        return "admin".equals(actor.getRole()) ? "p.deleted_at IS NULL" :
                "p.deleted_at IS NULL AND (p.uploader_id=? OR (p.visibility='shared' AND p.review_status='approved'))";
    }
    public static Object[] parameters(SessionPrincipal actor) {
        return "admin".equals(actor.getRole()) ? new Object[0] : new Object[]{actor.getId()};
    }
}

