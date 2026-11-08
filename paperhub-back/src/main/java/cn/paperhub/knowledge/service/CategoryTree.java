package cn.paperhub.knowledge.service;

import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.knowledge.entity.CategoryEntity;
import java.util.*;

/** Iterative tree operations also reject corrupt legacy cycles instead of recursing forever. */
final class CategoryTree {
    static final int MAX_DEPTH=10;
    private final Map<Long,CategoryEntity> nodes=new LinkedHashMap<>();
    private final Map<Long,List<CategoryEntity>> children=new HashMap<>();
    CategoryTree(List<CategoryEntity> categories) {
        for(CategoryEntity category:categories) {
            nodes.put(category.getId(),category);
            children.computeIfAbsent(category.getParentId(),ignored->new ArrayList<>()).add(category);
        }
        for(Long id:nodes.keySet()) {
            if(depth(id)>MAX_DEPTH)
                throw new BusinessException(ErrorCode.CONFLICT,"现有分类树超过10层，需要先修复");
        }
    }
    CategoryEntity find(long id) { return nodes.get(id); }
    int depth(Long id) {
        Set<Long> seen=new HashSet<>();
        int depth=0;
        while(id!=null) {
            if(!seen.add(id)) corrupt();
            CategoryEntity category=nodes.get(id);
            if(category==null) corrupt();
            depth++; id=category.getParentId();
        }
        return depth;
    }
    List<CategoryEntity> subtree(long id) {
        List<CategoryEntity> result=new ArrayList<>();
        Deque<CategoryEntity> frontier=new ArrayDeque<>();
        CategoryEntity root=nodes.get(id);
        if(root==null) return result;
        frontier.add(root); Set<Long> seen=new HashSet<>();
        while(!frontier.isEmpty()) {
            CategoryEntity node=frontier.removeFirst();
            if(!seen.add(node.getId())) corrupt();
            result.add(node);
            frontier.addAll(children.getOrDefault(node.getId(),Collections.emptyList()));
        }
        return result;
    }
    Map<Long,Long> inclusiveCounts(Map<Long,Long> direct) {
        Map<Long,Long> counts=new HashMap<>();
        for(Map.Entry<Long,Long> entry:direct.entrySet()) {
            Long id=entry.getKey(); Set<Long> seen=new HashSet<>();
            while(id!=null && nodes.containsKey(id)) {
                if(!seen.add(id)) corrupt();
                counts.merge(id,entry.getValue(),Long::sum);
                id=nodes.get(id).getParentId();
            }
        }
        return counts;
    }
    private void corrupt() { throw new BusinessException(ErrorCode.CONFLICT,"分类结构存在环或缺失父级，需要先修复"); }
}

