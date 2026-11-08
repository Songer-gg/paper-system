package cn.paperhub.admin.service;

import cn.paperhub.admin.entity.*;
import cn.paperhub.admin.mapper.*;
import cn.paperhub.admin.vo.*;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.audit.service.AuditService;
import cn.paperhub.security.SessionPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminHistoryService {
    private final ReviewRecordMapper reviews;
    private final DuplicatePairMapper pairs;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public AdminHistoryService(ReviewRecordMapper reviews,DuplicatePairMapper pairs,JdbcTemplate jdbc,AuditService audit) {
        this.reviews=reviews; this.pairs=pairs; this.jdbc=jdbc; this.audit=audit;
    }
    @Transactional(readOnly=true)
    public List<ReviewRecordView> reviews(String action) {
        if(!Arrays.asList("all","approved","rejected").contains(action)) invalid();
        QueryWrapper<ReviewRecordEntity> query=new QueryWrapper<>();
        if(!"all".equals(action)) query.eq("action",action);
        List<ReviewRecordEntity> values=reviews.selectList(query.orderByDesc("reviewed_at","id"));
        Set<Long> ids=new LinkedHashSet<>();
        for(ReviewRecordEntity value:values) { ids.add(value.getUploaderId()); ids.add(value.getReviewerId()); }
        Map<Long,String> names=names(ids); List<ReviewRecordView> result=new ArrayList<>();
        for(ReviewRecordEntity value:values) result.add(new ReviewRecordView(value,names.get(value.getUploaderId()),names.get(value.getReviewerId())));
        return result;
    }
    @Transactional(readOnly=true)
    public List<DuplicatePairView> pairs(String status,SessionPrincipal actor) {
        if(!Arrays.asList("all","pending","resolved").contains(status)) invalid();
        QueryWrapper<DuplicatePairEntity> query=new QueryWrapper<>();
        if("pending".equals(status)) query.eq("status","pending");
        if("resolved".equals(status)) query.ne("status","pending");
        List<DuplicatePairEntity> values=pairs.selectList(query.orderByDesc("submitted_at","id"));
        auditPrivateAssociations(values,actor,"duplicatePairList");
        List<DuplicatePairView> result=new ArrayList<>();
        for(DuplicatePairEntity value:values) result.add(new DuplicatePairView(value)); return result;
    }
    @Transactional(readOnly=true)
    public List<DuplicateRecordView> duplicateRecords(SessionPrincipal actor) {
        List<DuplicatePairEntity> values=pairs.selectList(new QueryWrapper<DuplicatePairEntity>().ne("status","pending")
                .orderByDesc("handled_at","id"));
        auditPrivateAssociations(values,actor,"duplicateRecordList");
        Set<Long> ids=new LinkedHashSet<>();
        for(DuplicatePairEntity value:values) if(value.getHandlerId()!=null) ids.add(value.getHandlerId());
        Map<Long,String> names=names(ids); List<DuplicateRecordView> result=new ArrayList<>();
        for(DuplicatePairEntity value:values) result.add(new DuplicateRecordView(value,names.get(value.getHandlerId())));
        return result;
    }
    private void auditPrivateAssociations(List<DuplicatePairEntity> values,SessionPrincipal actor,String action) {
        Set<Long> ids=new LinkedHashSet<>();
        for(DuplicatePairEntity value:values) { ids.add(value.getOriginalPaperId()); ids.add(value.getNewPaperId()); }
        List<Long> list=new ArrayList<>(ids); Map<Long,Long> owners=new LinkedHashMap<>();
        for(int start=0;start<list.size();start+=500) {
            List<Long> batch=list.subList(start,Math.min(start+500,list.size()));
            jdbc.query("SELECT id,uploader_id FROM paper WHERE visibility='private' AND id IN ("+marks(batch.size())+")",
                    (RowCallbackHandler)rs->owners.put(rs.getLong(1),rs.getLong(2)),batch.toArray());
        }
        if(!owners.isEmpty()) audit.sensitiveResources(actor,"paper",owners,action,true);
    }
    private Map<Long,String> names(Set<Long> ids) {
        Map<Long,String> result=new HashMap<>(); List<Long> list=new ArrayList<>(ids);
        for(int start=0;start<list.size();start+=500) {
            List<Long> batch=list.subList(start,Math.min(start+500,list.size()));
            jdbc.query("SELECT id,name FROM `user` WHERE id IN ("+marks(batch.size())+")",
                    (RowCallbackHandler)rs->result.put(rs.getLong(1),rs.getString(2)),batch.toArray());
        }
        return result;
    }
    private String marks(int size) { return String.join(",",Collections.nCopies(size,"?")); }
    private void invalid() { throw new BusinessException(ErrorCode.INVALID_PARAM); }
}

