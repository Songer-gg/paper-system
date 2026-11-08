package cn.paperhub.admin.mapper;

import cn.paperhub.admin.vo.AdminUserView;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdminUserMapper {
    String COLUMNS="u.id,u.username,u.name,u.email,u.role,u.status,u.registered_at,u.password_reset_at,"+
            "(SELECT COUNT(*) FROM paper p WHERE p.uploader_id=u.id AND p.visibility='shared' AND p.deleted_at IS NULL) AS shared_count,"+
            "(SELECT COUNT(*) FROM paper p WHERE p.uploader_id=u.id AND p.visibility='shared' AND p.review_status='approved' AND p.deleted_at IS NULL) AS contribution_count ";
    @Select("<script>SELECT "+COLUMNS+"FROM `user` u WHERE 1=1 "+
            "<if test='keyword != null'>AND (LOWER(u.name) LIKE #{keyword} ESCAPE '!' OR LOWER(u.username) LIKE #{keyword} ESCAPE '!') </if>"+
            "<if test='status != null and status != &quot;all&quot;'>AND u.status=#{status} </if>"+
            "<if test='from != null'>AND u.registered_at &gt;= #{from} </if>"+
            "<if test='to != null'>AND u.registered_at &lt;= #{to} </if> ORDER BY u.registered_at DESC,u.id DESC</script>")
    List<AdminUserView> list(@Param("keyword") String keyword,@Param("status") String status,
                           @Param("from") LocalDateTime from,@Param("to") LocalDateTime to);
    @Select("SELECT "+COLUMNS+"FROM `user` u WHERE u.id=#{id}")
    AdminUserView detail(@Param("id") long id);
}
