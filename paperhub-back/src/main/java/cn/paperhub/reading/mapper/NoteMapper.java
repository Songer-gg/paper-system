package cn.paperhub.reading.mapper;

import cn.paperhub.reading.entity.NoteEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface NoteMapper extends BaseMapper<NoteEntity> {
    @Select("SELECT id,user_id,paper_id FROM note WHERE id=#{id} AND user_id=#{owner}")
    NoteEntity reference(@Param("id") long id,@Param("owner") long owner);

    @Select("SELECT * FROM note WHERE id=#{id} AND user_id=#{owner} FOR UPDATE")
    NoteEntity lockOwned(@Param("id") long id,@Param("owner") long owner);

    @Update("UPDATE note SET title=#{note.title},content=#{note.content},paper_id=#{note.paperId},"+
            "paper_title=#{note.paperTitle},updated_at=#{note.updatedAt} WHERE id=#{note.id} AND user_id=#{note.userId}")
    int edit(@Param("note") NoteEntity note);

    @Delete("DELETE FROM note WHERE id=#{id} AND user_id=#{owner}")
    int deleteOwned(@Param("id") long id,@Param("owner") long owner);

    @Select("<script>SELECT n.*,p.uploader_id AS source_owner_id,p.visibility AS source_visibility,"+
            "CASE WHEN p.deleted_at IS NULL AND p.id IS NOT NULL "+
            "<if test='!admin'>AND (p.uploader_id=#{owner} OR (p.visibility='shared' AND p.review_status='approved')) </if>"+
            "THEN 1 ELSE 0 END AS paper_available FROM note n LEFT JOIN paper p ON p.id=n.paper_id "+
            "WHERE n.user_id=#{owner} <if test='paperId != null'>AND n.paper_id=#{paperId} </if>"+
            "<if test='keyword != null'>AND (LOWER(n.title) LIKE #{keyword} ESCAPE '!' "+
            "OR LOWER(n.paper_title) LIKE #{keyword} ESCAPE '!') </if> ORDER BY n.updated_at DESC,n.id DESC</script>")
    List<NoteEntity> owned(@Param("owner") long owner,@Param("admin") boolean admin,
                          @Param("paperId") Long paperId,@Param("keyword") String keyword);
}

