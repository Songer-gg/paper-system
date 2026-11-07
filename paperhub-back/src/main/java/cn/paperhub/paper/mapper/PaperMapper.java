package cn.paperhub.paper.mapper;

import cn.paperhub.paper.entity.PaperEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PaperMapper extends BaseMapper<PaperEntity> {
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

    @Insert("INSERT INTO duplicate_pair(original_paper_id,new_paper_id,original_paper_title,new_paper_title," +
            "doi_match,file_hash_match,submitted_at,title_similarity,reason,status) " +
            "VALUES(#{original.id},#{paper.id},#{original.title},#{paper.title},0,0,#{time},#{similarity}," +
            "'标题相似，需人工判断','pending') ON DUPLICATE KEY UPDATE id=id")
    int suspect(@Param("original") PaperEntity original, @Param("paper") PaperEntity paper,
                @Param("time") java.time.LocalDateTime time, @Param("similarity") java.math.BigDecimal similarity);
}

