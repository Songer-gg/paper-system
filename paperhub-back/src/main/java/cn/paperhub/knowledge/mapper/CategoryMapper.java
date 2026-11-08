package cn.paperhub.knowledge.mapper;

import cn.paperhub.knowledge.entity.CategoryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CategoryMapper extends BaseMapper<CategoryEntity> {
    @Update("UPDATE category SET name=#{name} WHERE id=#{id}")
    int rename(@Param("id") long id, @Param("name") String name);

    @Update("UPDATE category SET parent_id=#{parentId} WHERE id=#{id}")
    int move(@Param("id") long id, @Param("parentId") Long parentId);
}

