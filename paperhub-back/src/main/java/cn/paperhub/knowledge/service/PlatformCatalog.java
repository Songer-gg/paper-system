package cn.paperhub.knowledge.service;

import cn.paperhub.knowledge.entity.*;
import cn.paperhub.knowledge.mapper.*;
import cn.paperhub.knowledge.vo.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformCatalog {
    private final CategoryMapper categories;
    private final TagMapper tags;
    public PlatformCatalog(CategoryMapper categories,TagMapper tags) { this.categories=categories; this.tags=tags; }
    @Transactional(readOnly=true)
    public List<Long> descendants(long id) {
        return new CategoryTree(platformCategories()).subtree(id).stream().map(CategoryEntity::getId).collect(Collectors.toList());
    }
    @Transactional(readOnly=true)
    public List<CategoryView> countedCategories(Map<Long,Long> direct) {
        List<CategoryEntity> values=platformCategories();
        Map<Long,Long> totals=new CategoryTree(values).inclusiveCounts(direct);
        List<CategoryView> result=new ArrayList<>();
        for(CategoryEntity category:values) {
            long count=totals.getOrDefault(category.getId(),0L);
            if(count>0) result.add(new CategoryView(category,count));
        }
        return result;
    }
    @Transactional(readOnly=true)
    public List<TagView> countedTags(Map<Long,Long> counts) {
        if(counts.isEmpty()) return Collections.emptyList();
        List<TagView> result=new ArrayList<>();
        for(TagEntity tag:tags.selectList(new QueryWrapper<TagEntity>().isNull("owner_id").in("id",counts.keySet()).orderByAsc("id")))
            result.add(new TagView(tag,counts.get(tag.getId())));
        return result;
    }
    private List<CategoryEntity> platformCategories() {
        return categories.selectList(new QueryWrapper<CategoryEntity>().isNull("owner_id").orderByAsc("id"));
    }
}

