package cn.paperhub.knowledge.service;

import cn.paperhub.audit.service.AuditService;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.knowledge.entity.CategoryEntity;
import cn.paperhub.knowledge.mapper.*;
import cn.paperhub.knowledge.vo.CategoryView;
import cn.paperhub.paper.service.*;
import cn.paperhub.paper.vo.PaperView;
import cn.paperhub.security.SessionPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
    private final CategoryMapper mapper;
    private final KnowledgeRelationMapper relations;
    private final KnowledgeCounts counts;
    private final PaperListingService papers;
    private final AuditService audit;
    private final Clock clock;
    public CategoryService(CategoryMapper mapper, KnowledgeRelationMapper relations, KnowledgeCounts counts,
                           PaperListingService papers, AuditService audit, Clock clock) {
        this.mapper=mapper; this.relations=relations; this.counts=counts;
        this.papers=papers; this.audit=audit; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public List<CategoryView> list(SessionPrincipal actor) {
        List<CategoryEntity> categories=visible(actor);
        auditCategories(categories,actor,"categoryList",true);
        Map<Long,Long> totals=new CategoryTree(categories).inclusiveCounts(counts.categories(actor));
        List<CategoryView> result=new ArrayList<>();
        for(CategoryEntity category:categories) result.add(new CategoryView(category,totals.getOrDefault(category.getId(),0L)));
        return result;
    }
    @Transactional(timeout=60)
    public CategoryView create(String name, Long parentId, SessionPrincipal actor) {
        name=KnowledgeAccess.name(name);
        if(parentId!=null) PaperValidation.id(parentId);
        CategoryTree tree=writeTree();
        Long owner=KnowledgeAccess.admin(actor)?null:actor.getId();
        if(parentId!=null) {
            CategoryEntity parent=readable(tree,parentId,actor);
            auditCategories(Collections.singletonList(parent),actor,"categoryCreateParent",true);
            parentScope(owner,parent);
            if(tree.depth(parentId)+1>CategoryTree.MAX_DEPTH) tooDeep();
        }
        CategoryEntity category=new CategoryEntity();
        category.setName(name); category.setParentId(parentId); category.setOwnerId(owner);
        category.setCustom(owner!=null); category.setCreatedAt(LocalDateTime.now(clock));
        mapper.insert(category); PaperValidation.id(category.getId());
        audit.mutation(actor,"分类创建",target(category));
        return new CategoryView(category,0);
    }
    @Transactional(timeout=60)
    public boolean rename(long id, String name, SessionPrincipal actor) {
        PaperValidation.id(id); name=KnowledgeAccess.name(name);
        CategoryEntity category=writable(writeTree(),id,actor);
        auditCategories(Collections.singletonList(category),actor,"categoryRenameAccess",true);
        if(!name.equals(category.getName())) {
            if(mapper.rename(id,name)!=1) conflict();
            audit.mutation(actor,"分类重命名",target(category));
        }
        return true;
    }
    @Transactional(timeout=60)
    public boolean move(long id, Long parentId, SessionPrincipal actor) {
        PaperValidation.id(id); if(parentId!=null) PaperValidation.id(parentId);
        CategoryTree tree=writeTree();
        CategoryEntity category=writable(tree,id,actor);
        List<CategoryEntity> subtree=tree.subtree(id);
        List<CategoryEntity> sensitive=new ArrayList<>(subtree);
        CategoryEntity parent=parentId==null?null:readable(tree,parentId,actor);
        if(parent!=null) sensitive.add(parent);
        auditCategories(sensitive,actor,"categoryMoveAccess",true);
        if(subtree.stream().anyMatch(node->Objects.equals(node.getId(),parentId)))
            throw new BusinessException(ErrorCode.BIZ_REJECTED,"不能移动到自身或后代分类下");
        if(parent!=null) parentScope(category.getOwnerId(),parent);
        int rootDepth=tree.depth(id);
        int height=1;
        for(CategoryEntity node:subtree) height=Math.max(height,tree.depth(node.getId())-rootDepth+1);
        if(tree.depth(parentId)+height>CategoryTree.MAX_DEPTH) tooDeep();
        if(!Objects.equals(parentId,category.getParentId())) {
            if(mapper.move(id,parentId)!=1) conflict();
            audit.mutation(actor,"分类移动",target(category)+";parentId="+parentId);
        }
        return true;
    }
    @Transactional(timeout=60)
    public List<String> delete(long id, SessionPrincipal actor) {
        PaperValidation.id(id);
        CategoryTree tree=writeTree();
        CategoryEntity category=writable(tree,id,actor);
        List<CategoryEntity> subtree=tree.subtree(id);
        if(subtree.stream().anyMatch(node->!Objects.equals(node.getOwnerId(),category.getOwnerId()))) {
            auditCategories(subtree,actor,"categoryDeleteScopeRejected",false);
            throw new BusinessException(ErrorCode.CONFLICT,"子树包含其他归属的自定义分类，请先移动后再删除");
        }
        auditCategories(subtree,actor,"categoryDeleteAccess",true);
        List<Long> ids=subtree.stream().map(CategoryEntity::getId).collect(Collectors.toList());
        List<Long> affected=relations.papersInCategories(ids);
        // All category-changing writers first lock the dictionary; the affected set is stable here.
        for(Long paperId:affected) {
            relations.lockPaper(paperId);
            if(relations.clearCategory(paperId)!=1) conflict();
        }
        List<CategoryEntity> deletion=new ArrayList<>(subtree); Collections.reverse(deletion);
        for(CategoryEntity node:deletion) if(mapper.deleteById(node.getId())!=1) conflict();
        audit.mutation(actor,"分类删除",target(category)+";categoryCount="+ids.size()+";paperCount="+affected.size());
        return subtree.stream().map(CategoryEntity::getName).collect(Collectors.toList());
    }
    @Transactional(readOnly=true)
    public List<PaperView> papers(long id, SessionPrincipal actor) {
        PaperValidation.id(id);
        CategoryTree tree=new CategoryTree(visible(actor));
        CategoryEntity category=readable(tree,id,actor);
        auditCategories(Collections.singletonList(category),actor,"categoryPaperListAccess",true);
        return papers.categories(tree.subtree(id).stream().map(CategoryEntity::getId).collect(Collectors.toList()),actor);
    }
    private List<CategoryEntity> visible(SessionPrincipal actor) {
        QueryWrapper<CategoryEntity> query=new QueryWrapper<>();
        if(!KnowledgeAccess.admin(actor)) query.and(scope->scope.isNull("owner_id").or().eq("owner_id",actor.getId()));
        return mapper.selectList(query.orderByAsc("id"));
    }
    private CategoryTree writeTree() {
        return new CategoryTree(mapper.selectList(new QueryWrapper<CategoryEntity>().orderByAsc("id").last("FOR UPDATE")));
    }
    private CategoryEntity readable(CategoryTree tree, long id, SessionPrincipal actor) {
        CategoryEntity category=tree.find(id);
        if(category==null) throw new BusinessException(ErrorCode.NOT_FOUND);
        KnowledgeAccess.readable(actor,category.getOwnerId());
        return category;
    }
    private CategoryEntity writable(CategoryTree tree, long id, SessionPrincipal actor) {
        CategoryEntity category=readable(tree,id,actor);
        KnowledgeAccess.writable(actor,category.getOwnerId());
        return category;
    }
    private void parentScope(Long owner, CategoryEntity parent) {
        if(parent.getOwnerId()!=null && !Objects.equals(owner,parent.getOwnerId()))
            throw new BusinessException(ErrorCode.BIZ_REJECTED,"分类不能挂到其他归属的自定义分类下");
    }
    private void auditCategories(List<CategoryEntity> categories, SessionPrincipal actor, String action, boolean success) {
        if(!KnowledgeAccess.admin(actor)) return;
        Map<Long,Long> owners=new LinkedHashMap<>();
        for(CategoryEntity category:categories)
            if(category.getOwnerId()!=null) owners.put(category.getId(),category.getOwnerId());
        if(!owners.isEmpty()) audit.sensitiveResources(actor,"category",owners,action,success);
    }
    private String target(CategoryEntity category) { return "categoryId="+category.getId()+";ownerId="+category.getOwnerId(); }
    private void conflict() { throw new BusinessException(ErrorCode.CONFLICT); }
    private void tooDeep() { throw new BusinessException(ErrorCode.BIZ_REJECTED,"分类树最多10层"); }
}

