package cn.paperhub.admin.mapper;

import cn.paperhub.admin.entity.ReportEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ReportMapper extends BaseMapper<ReportEntity> {
    @Select("SELECT id,paper_id FROM report WHERE id=#{id}")
    ReportEntity reference(@Param("id") long id);
    @Select("SELECT * FROM report WHERE id=#{id} FOR UPDATE")
    ReportEntity lock(@Param("id") long id);
    @Update("UPDATE report SET status='resolved',result=#{result},handler_id=#{actor},handled_at=#{time} "+
            "WHERE id=#{id} AND paper_id=#{paperId} AND status='pending'")
    int resolve(@Param("id") long id,@Param("paperId") long paperId,@Param("actor") long actor,
                @Param("result") String result,@Param("time") LocalDateTime time);
    @Select("<script>SELECT r.*,u.name AS reporter_name FROM report r LEFT JOIN `user` u ON u.id=r.reporter_id "+
            "<if test='status != &quot;all&quot;'>WHERE r.status=#{status} </if>"+
            "ORDER BY <choose><when test='history'>r.handled_at</when><otherwise>r.created_at</otherwise></choose> DESC,r.id DESC</script>")
    List<ReportEntity> list(@Param("status") String status,@Param("history") boolean history);
}
