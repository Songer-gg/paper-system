package cn.paperhub.reading.service;

import cn.paperhub.audit.service.AuditService;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.mapper.PaperMapper;
import cn.paperhub.paper.service.PaperListingService;
import cn.paperhub.paper.service.PaperValidation;
import cn.paperhub.paper.vo.PaperView;
import cn.paperhub.reading.entity.FavoriteEntity;
import cn.paperhub.reading.mapper.FavoriteMapper;
import cn.paperhub.reading.vo.*;
import cn.paperhub.security.SessionPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteService {
    private final FavoriteMapper favorites;
    private final PaperMapper papers;
    private final PaperListingService listing;
    private final PersonalPaperAccess access;
    private final AuditService audit;
    private final Clock clock;
    public FavoriteService(FavoriteMapper favorites,PaperMapper papers,PaperListingService listing,
                           PersonalPaperAccess access,AuditService audit,Clock clock) {
        this.favorites=favorites; this.papers=papers; this.listing=listing; this.access=access; this.audit=audit; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public List<FavoriteView> list(SessionPrincipal actor) {
        List<FavoriteEntity> rows=favorites.selectList(new QueryWrapper<FavoriteEntity>().eq("user_id",actor.getId())
                .apply("EXISTS (SELECT 1 FROM paper p WHERE p.id=favorite.paper_id AND p.visibility='shared' "+
                        "AND p.review_status='approved' AND p.deleted_at IS NULL)").orderByDesc("favorited_at","id"));
        List<PaperEntity> values=new ArrayList<>(); List<Long> ids=new ArrayList<>();
        for(FavoriteEntity row:rows) ids.add(row.getPaperId());
        for(int start=0;start<ids.size();start+=500)
            values.addAll(papers.selectList(new QueryWrapper<PaperEntity>().in("id",ids.subList(start,Math.min(start+500,ids.size())))
                    .eq("visibility","shared").eq("review_status","approved").isNull("deleted_at")));
        Map<Long,PaperView> views=new HashMap<>();
        for(PaperView value:listing.views(values,actor,"ownFavorites")) views.put(value.getId(),value);
        List<FavoriteView> result=new ArrayList<>();
        for(FavoriteEntity row:rows) if(views.containsKey(row.getPaperId()))
            result.add(new FavoriteView(row.getPaperId(),views.get(row.getPaperId()),row.getFavoritedAt()));
        return result;
    }
    @Transactional(timeout=60)
    public FavoriteState set(long paperId,Boolean target,SessionPrincipal actor) {
        PaperValidation.id(paperId);
        // Serializes this paper's state check and even two toggles for the same missing favorite row.
        PaperEntity paper=access.lock(paperId);
        FavoriteEntity existing=favorites.selectOne(new QueryWrapper<FavoriteEntity>().eq("user_id",actor.getId())
                .eq("paper_id",paperId).last("FOR UPDATE"));
        boolean desired=target!=null?target:existing==null;
        if(desired) {
            access.visible(paper,actor,"favoriteAccess");
            if(!"shared".equals(paper.getVisibility()) || !"approved".equals(paper.getReviewStatus()))
                throw new BusinessException(ErrorCode.BIZ_REJECTED,"只能收藏已通过的共享论文");
            if(existing==null) {
                FavoriteEntity row=new FavoriteEntity();
                row.setUserId(actor.getId()); row.setPaperId(paperId); row.setFavoritedAt(LocalDateTime.now(clock).withNano(0));
                favorites.insert(row); PaperValidation.id(row.getId());
                audit.mutation(actor,"论文收藏","paperId="+paperId+";ownerId="+actor.getId());
            }
        } else if(existing!=null) {
            if(favorites.delete(new QueryWrapper<FavoriteEntity>().eq("id",existing.getId()).eq("user_id",actor.getId()))!=1)
                throw new BusinessException(ErrorCode.CONFLICT);
            audit.mutation(actor,"取消收藏","paperId="+paperId+";ownerId="+actor.getId());
        }
        return new FavoriteState(paperId,desired);
    }
}

