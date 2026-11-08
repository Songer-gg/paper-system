package cn.paperhub.paper.mapper;

import cn.paperhub.paper.entity.PaperEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PaperMapper extends BaseMapper<PaperEntity> {
    @Select("SELECT * FROM paper WHERE id=#{id} FOR UPDATE")
    @Results(id="lockedPaper",value={
            @Result(column="authors",property="authors",typeHandler=StringListTypeHandler.class),
            @Result(column="keywords",property="keywords",typeHandler=StringListTypeHandler.class),
            @Result(column="abstract",property="abstractText")})
    PaperEntity lockById(@Param("id") long id);
    @Select("SELECT id FROM paper WHERE id=#{id} AND deleted_at IS NULL FOR UPDATE")
    Long lockActive(@Param("id") long id);

    @Select("SELECT tag_id FROM paper_tag WHERE paper_id=#{id} ORDER BY tag_id")
    List<Long> tagIds(@Param("id") long id);

    @Delete("DELETE FROM paper_tag WHERE paper_id=#{id}")
    int deleteTags(@Param("id") long id);

    @Insert("INSERT INTO paper_tag(paper_id,tag_id) VALUES(#{id},#{tagId})")
    int insertTag(@Param("id") long id, @Param("tagId") long tagId);

    @Update("UPDATE paper SET downloads=downloads+1 WHERE id=#{id} AND deleted_at IS NULL")
    int incrementDownloads(@Param("id") long id);

    @Update("UPDATE paper SET deleted_at=#{time},version=version+1 WHERE id=#{id} AND deleted_at IS NULL AND version=#{version}")
    int softDelete(@Param("id") long id, @Param("version") long version, @Param("time") java.time.LocalDateTime time);

}

