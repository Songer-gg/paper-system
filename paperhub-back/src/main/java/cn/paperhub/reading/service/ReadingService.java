package cn.paperhub.reading.service;

import cn.paperhub.audit.service.AuditService;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.mapper.PaperMapper;
import cn.paperhub.paper.service.PaperValidation;
import cn.paperhub.reading.entity.ReadingEntity;
import cn.paperhub.reading.mapper.ReadingMapper;
import cn.paperhub.reading.vo.ReadingView;
import cn.paperhub.security.SessionPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReadingService {
    private final ReadingMapper readings;
    private final PaperMapper papers;
    private final PersonalPaperAccess access;
    private final AuditService audit;
    private final Clock clock;
    public ReadingService(ReadingMapper readings,PaperMapper papers,PersonalPaperAccess access,AuditService audit,Clock clock) {
        this.readings=readings; this.papers=papers; this.access=access; this.audit=audit; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public List<ReadingView> list(SessionPrincipal actor) {
        QueryWrapper<ReadingEntity> query=new QueryWrapper<ReadingEntity>().eq("user_id",actor.getId());
        if("admin".equals(actor.getRole()))
            query.apply("EXISTS (SELECT 1 FROM paper p WHERE p.id=reading_record.paper_id AND p.deleted_at IS NULL)");
        else query.apply("EXISTS (SELECT 1 FROM paper p WHERE p.id=reading_record.paper_id AND p.deleted_at IS NULL "+
                "AND (p.uploader_id={0} OR (p.visibility='shared' AND p.review_status='approved')))",actor.getId());
        List<ReadingEntity> rows=readings.selectList(query.orderByDesc("last_read_at","id"));
        if("admin".equals(actor.getRole())) {
            List<Long> ids=new ArrayList<>(); for(ReadingEntity row:rows) ids.add(row.getPaperId());
            Map<Long,Long> owners=new LinkedHashMap<>();
            for(int start=0;start<ids.size();start+=500)
                for(PaperEntity paper:papers.selectList(new QueryWrapper<PaperEntity>().eq("visibility","private")
                        .in("id",ids.subList(start,Math.min(start+500,ids.size()))).select("id","uploader_id")))
                    owners.put(paper.getId(),paper.getUploaderId());
            if(!owners.isEmpty()) audit.sensitiveResources(actor,"paper",owners,"ownReadingList",true);
        }
        List<ReadingView> result=new ArrayList<>(); for(ReadingEntity row:rows) result.add(new ReadingView(row));
        return result;
    }
    @Transactional(timeout=60)
    public ReadingView change(long paperId,String requested,SessionPrincipal actor) {
        PaperValidation.id(paperId);
        if(requested!=null && !Arrays.asList("unread","reading","read","paused").contains(requested))
            throw new BusinessException(ErrorCode.INVALID_PARAM);
        access.visible(access.lock(paperId),actor,"readingStateAccess");
        ReadingEntity row=readings.selectOne(new QueryWrapper<ReadingEntity>().eq("user_id",actor.getId())
                .eq("paper_id",paperId).last("FOR UPDATE"));
        LocalDateTime now=LocalDateTime.now(clock).withNano(0);
        if(row==null) {
            row=new ReadingEntity(); row.setUserId(actor.getId()); row.setPaperId(paperId);
            row.setStatus(requested==null?"unread":requested); row.setAddedAt(now); row.setLastReadAt(now);
            readings.insert(row); PaperValidation.id(row.getId());
            audit.mutation(actor,"加入阅读","paperId="+paperId+";ownerId="+actor.getId()+";status="+row.getStatus());
        } else if(requested!=null && (!requested.equals(row.getStatus()) || !now.equals(row.getLastReadAt()))) {
            if(readings.status(row.getId(),actor.getId(),requested,now)!=1) throw new BusinessException(ErrorCode.CONFLICT);
            row.setStatus(requested); row.setLastReadAt(now);
            audit.mutation(actor,"阅读状态更新","paperId="+paperId+";ownerId="+actor.getId()+";status="+requested);
        }
        return new ReadingView(row);
    }
}

