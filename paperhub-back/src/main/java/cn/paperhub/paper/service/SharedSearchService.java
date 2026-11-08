package cn.paperhub.paper.service;

import cn.paperhub.admin.dto.AdminPaperQuery;
import cn.paperhub.knowledge.service.PlatformCatalog;
import cn.paperhub.paper.dto.SharedQuery;
import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.mapper.PaperMapper;
import cn.paperhub.paper.vo.*;
import cn.paperhub.security.SessionPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.*;
import java.util.stream.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SharedSearchService {
    private final PaperMapper mapper;
    private final PaperListingService listing;
    private final PlatformCatalog catalog;
    private final JdbcTemplate jdbc;
    public SharedSearchService(PaperMapper mapper,PaperListingService listing,PlatformCatalog catalog,JdbcTemplate jdbc) {
        this.mapper=mapper; this.listing=listing; this.catalog=catalog; this.jdbc=jdbc;
    }
    @Transactional(readOnly=true)
    public PageView<PaperView> search(SharedQuery input,SessionPrincipal actor) {
        QueryWrapper<PaperEntity> query=publicPool();
        String q=clean(input.getQ());
        if(!q.isEmpty()) {
            String like=pattern(q);
            query.apply("(LOWER(title) LIKE {0} ESCAPE '!' OR LOWER(abstract) LIKE {0} ESCAPE '!' "+
                    "OR LOWER(COALESCE(doi,'')) LIKE {0} ESCAPE '!' OR LOWER(COALESCE(journal,'')) LIKE {0} ESCAPE '!' "+
                    "OR EXISTS (SELECT 1 FROM JSON_TABLE(paper.authors,'$[*]' COLUMNS(entry VARCHAR(120) PATH '$')) a "+
                    "WHERE LOWER(a.entry) LIKE {0} ESCAPE '!') "+
                    "OR EXISTS (SELECT 1 FROM JSON_TABLE(paper.keywords,'$[*]' COLUMNS(entry VARCHAR(120) PATH '$')) k "+
                    "WHERE LOWER(k.entry) LIKE {0} ESCAPE '!'))",like);
        }
        category(query,input.getCategoryId());
        if(input.getYear()!=null) query.eq("year",input.getYear());
        if(!clean(input.getJournal()).isEmpty()) query.eq("journal",clean(input.getJournal()));
        List<Long> tags=parseTags(input.getTagIds());
        if(!tags.isEmpty()) {
            String placeholders=IntStream.range(0,tags.size()).mapToObj(i->"{"+i+"}").collect(Collectors.joining(","));
            query.apply("EXISTS (SELECT 1 FROM paper_tag pt WHERE pt.paper_id=paper.id AND pt.tag_id IN ("+placeholders+"))",tags.toArray());
        }
        long total=mapper.selectCount(query);
        long offset=((long)input.getPage()-1)*input.getPageSize();
        if(offset>=total) return new PageView<>(Collections.emptyList(),total);
        String sort=input.getSort();
        if("downloads-desc".equals(sort)) query.orderByDesc("downloads");
        else if("year-desc".equals(sort)) query.orderByDesc("year");
        else query.orderByDesc("uploaded_at");
        query.orderByDesc("id").last("LIMIT "+input.getPageSize()+" OFFSET "+offset);
        return new PageView<>(listing.views(mapper.selectList(query),actor,"sharedSearch"),total);
    }
    @Transactional(readOnly=true)
    public PaperView detail(long id,SessionPrincipal actor) {
        PaperValidation.id(id);
        PaperEntity paper=mapper.selectOne(publicPool().eq("id",id));
        if(paper==null) throw new cn.paperhub.common.exception.BusinessException(cn.paperhub.common.api.ErrorCode.NOT_FOUND);
        return listing.views(Collections.singletonList(paper),actor,"sharedDetail").get(0);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> options() {
        Map<Long,Long> categoryCounts=new HashMap<>(),tagCounts=new HashMap<>();
        jdbc.query("SELECT category_id,COUNT(*) FROM paper WHERE "+PUBLIC_SQL+" AND category_id IS NOT NULL GROUP BY category_id",
                (RowCallbackHandler)rs->categoryCounts.put(rs.getLong(1),rs.getLong(2)));
        jdbc.query("SELECT pt.tag_id,COUNT(*) FROM paper p JOIN paper_tag pt ON pt.paper_id=p.id "+
                "WHERE p.visibility='shared' AND p.review_status='approved' AND p.deleted_at IS NULL GROUP BY pt.tag_id",
                (RowCallbackHandler)rs->tagCounts.put(rs.getLong(1),rs.getLong(2)));
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("categories",catalog.countedCategories(categoryCounts)); result.put("tags",catalog.countedTags(tagCounts));
        result.put("years",jdbc.queryForList("SELECT DISTINCT year FROM paper WHERE "+PUBLIC_SQL+" ORDER BY year DESC",Integer.class));
        result.put("journals",jdbc.queryForList("SELECT DISTINCT journal FROM paper WHERE "+PUBLIC_SQL+
                " AND journal IS NOT NULL AND TRIM(journal)<>'' ORDER BY journal",String.class));
        return result;
    }
    @Transactional(readOnly=true)
    public List<PaperView> admin(AdminPaperQuery input,SessionPrincipal actor) {
        QueryWrapper<PaperEntity> query=new QueryWrapper<PaperEntity>().eq("visibility","shared").isNull("deleted_at");
        if(!clean(input.getTitle()).isEmpty()) query.apply("LOWER(title) LIKE {0} ESCAPE '!'",pattern(input.getTitle()));
        if(!clean(input.getDoi()).isEmpty()) query.apply("LOWER(COALESCE(doi,'')) LIKE {0} ESCAPE '!'",pattern(input.getDoi()));
        if(!clean(input.getUploader()).isEmpty()) query.apply(
                "EXISTS (SELECT 1 FROM `user` u WHERE u.id=paper.uploader_id AND LOWER(u.name) LIKE {0} ESCAPE '!')",pattern(input.getUploader()));
        category(query,input.getCategoryId());
        if(input.getStatus()!=null && !"all".equals(input.getStatus())) query.eq("review_status",input.getStatus());
        return listing.views(mapper.selectList(query.orderByDesc("uploaded_at","id")),actor,"adminSharedList");
    }
    private void category(QueryWrapper<PaperEntity> query,Long id) {
        if(id==null) return;
        PaperValidation.id(id); List<Long> descendants=catalog.descendants(id);
        if(descendants.isEmpty()) query.apply("1=0"); else query.in("category_id",descendants);
    }
    private List<Long> parseTags(String raw) {
        if(raw==null || raw.trim().isEmpty()) return Collections.emptyList();
        String[] parts=raw.split(",",-1);
        if(parts.length>50) PaperValidation.invalid();
        Set<Long> result=new LinkedHashSet<>();
        for(String part:parts) {
            if(!part.trim().matches("[1-9][0-9]*")) PaperValidation.invalid();
            try { long id=Long.parseLong(part.trim()); PaperValidation.id(id); result.add(id); }
            catch(NumberFormatException ex) { PaperValidation.invalid(); }
        }
        return new ArrayList<>(result);
    }
    private String clean(String value) { return value==null?"":value.trim(); }
    private String pattern(String value) {
        return "%"+clean(value).toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
    }
    private static final String PUBLIC_SQL="visibility='shared' AND review_status='approved' AND deleted_at IS NULL";
    private QueryWrapper<PaperEntity> publicPool() {
        return new QueryWrapper<PaperEntity>().eq("visibility","shared").eq("review_status","approved").isNull("deleted_at");
    }
}

