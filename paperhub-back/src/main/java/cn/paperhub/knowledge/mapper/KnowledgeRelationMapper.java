package cn.paperhub.knowledge.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface KnowledgeRelationMapper {
    @Select("<script>SELECT id FROM paper WHERE category_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY id</script>")
    List<Long> papersInCategories(@Param("ids") List<Long> ids);

    @Select("SELECT paper_id FROM paper_tag WHERE tag_id=#{tagId} ORDER BY paper_id")
    List<Long> papersWithTag(@Param("tagId") long tagId);

    // Lock one primary key at a time in the ascending order of the calling list, including deleted rows.
    @Select("SELECT id FROM paper WHERE id=#{id} FOR UPDATE")
    Long lockPaper(@Param("id") long id);

    @Update("UPDATE paper SET category_id=NULL,version=version+1 WHERE id=#{id}")
    int clearCategory(@Param("id") long id);

    @Update("UPDATE paper SET version=version+1 WHERE id=#{id}")
    int bumpVersion(@Param("id") long id);

    @Delete("DELETE FROM paper_tag WHERE tag_id=#{tagId}")
    int clearTag(@Param("tagId") long tagId);
}

