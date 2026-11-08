package cn.paperhub.knowledge.mapper;

import cn.paperhub.knowledge.entity.TagEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TagMapper extends BaseMapper<TagEntity> {
    @Update("UPDATE tag SET name=#{name},color=#{color} WHERE id=#{id}")
    int edit(@Param("id") long id, @Param("name") String name, @Param("color") String color);
}

