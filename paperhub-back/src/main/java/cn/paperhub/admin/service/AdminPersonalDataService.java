package cn.paperhub.admin.service;

import cn.paperhub.admin.vo.AdminReadingView;
import cn.paperhub.audit.service.AuditService;
import cn.paperhub.auth.mapper.UserAccountMapper;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.mapper.PaperMapper;
import cn.paperhub.paper.service.*;
import cn.paperhub.paper.vo.PaperView;
import cn.paperhub.reading.entity.*;
import cn.paperhub.reading.mapper.*;
import cn.paperhub.reading.vo.*;
import cn.paperhub.security.SessionPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class AdminPersonalDataService {
    private final UserAccountMapper users;
    private final PaperMapper papers;
    private final PaperListingService listing;
    private final FavoriteMapper favorites;
    private final ReadingMapper readings;
    private final NoteMapper notes;
    private final AuditService audit;
    public AdminPersonalDataService(UserAccountMapper users,PaperMapper papers,PaperListingService listing,
                                    FavoriteMapper favorites,ReadingMapper readings,NoteMapper notes,AuditService audit) {
        this.users=users;this.papers=papers;this.listing=listing;this.favorites=favorites;
        this.readings=readings;this.notes=notes;this.audit=audit;
    }
    public List<PaperView> papers(long owner,SessionPrincipal actor) {
        scope(owner,actor,"adminUserPapers");
        return listing.views(papers.selectList(new QueryWrapper<PaperEntity>().eq("uploader_id",owner)
                .isNull("deleted_at").orderByDesc("uploaded_at","id")),actor,"adminUserPapers");
    }
    public List<FavoriteView> favorites(long owner,SessionPrincipal actor) {
        scope(owner,actor,"adminUserFavorites");
        List<FavoriteEntity> rows=favorites.selectList(new QueryWrapper<FavoriteEntity>()
                .eq("user_id",owner).orderByDesc("favorited_at","id"));
        List<Long> ids=new ArrayList<>();Map<Long,Long> resources=new LinkedHashMap<>();
        for(FavoriteEntity row:rows) { ids.add(row.getPaperId());resources.put(row.getId(),owner); }
        recordAudit(actor,"favorite",resources,"adminUserFavorites");
        Map<Long,PaperView> views=paperViews(ids,actor,"adminFavoritePapers");
        List<FavoriteView> result=new ArrayList<>();
        for(FavoriteEntity row:rows) result.add(new FavoriteView(row.getPaperId(),views.get(row.getPaperId()),row.getFavoritedAt()));
        return result;
    }
    public List<AdminReadingView> readings(long owner,SessionPrincipal actor) {
        scope(owner,actor,"adminUserReadingRecords");
        List<ReadingEntity> rows=readings.selectList(new QueryWrapper<ReadingEntity>()
                .eq("user_id",owner).orderByDesc("last_read_at","id"));
        List<Long> ids=new ArrayList<>();Map<Long,Long> resources=new LinkedHashMap<>();
        for(ReadingEntity row:rows) { ids.add(row.getPaperId());resources.put(row.getId(),owner); }
        recordAudit(actor,"reading",resources,"adminUserReadingRecords");
        Map<Long,PaperView> views=paperViews(ids,actor,"adminReadingPapers");
        List<AdminReadingView> result=new ArrayList<>();
        for(ReadingEntity row:rows) result.add(new AdminReadingView(row,views.get(row.getPaperId())));
        return result;
    }
    public List<NoteView> notes(long owner,SessionPrincipal actor) {
        scope(owner,actor,"adminUserNotes");
        List<NoteEntity> rows=notes.owned(owner,true,null,null);
        Map<Long,Long> resources=new LinkedHashMap<>(),privateSources=new LinkedHashMap<>();
        for(NoteEntity row:rows) {
            resources.put(row.getId(),owner);
            if("private".equals(row.getSourceVisibility()) && row.getSourceOwnerId()!=null)
                privateSources.put(row.getPaperId(),row.getSourceOwnerId());
        }
        recordAudit(actor,"note",resources,"adminUserNotes");
        recordAudit(actor,"paper",privateSources,"adminNoteSourceSnapshots");
        List<NoteView> result=new ArrayList<>();
        for(NoteEntity row:rows) result.add(new NoteView(row,Boolean.TRUE.equals(row.getPaperAvailable())));
        return result;
    }
    private void scope(long owner,SessionPrincipal actor,String action) {
        AdminUserService.requireAdmin(actor);PaperValidation.id(owner);
        boolean found=users.selectById(owner)!=null;
        audit.sensitiveResources(actor,"user",Collections.singletonMap(owner,owner),action,found);
        if(!found) throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    private void recordAudit(SessionPrincipal actor,String kind,Map<Long,Long> resources,String action) {
        if(!resources.isEmpty()) audit.sensitiveResources(actor,kind,resources,action,true);
    }
    private Map<Long,PaperView> paperViews(List<Long> input,SessionPrincipal actor,String action) {
        List<Long> ids=new ArrayList<>(new LinkedHashSet<>(input));List<PaperEntity> rows=new ArrayList<>();
        for(int start=0;start<ids.size();start+=500)
            rows.addAll(papers.selectList(new QueryWrapper<PaperEntity>().isNull("deleted_at")
                    .in("id",ids.subList(start,Math.min(start+500,ids.size())))));
        Map<Long,PaperView> result=new HashMap<>();for(PaperView row:listing.views(rows,actor,action)) result.put(row.getId(),row);
        return result;
    }
}
