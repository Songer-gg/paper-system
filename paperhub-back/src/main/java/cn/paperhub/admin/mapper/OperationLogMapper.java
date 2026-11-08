package cn.paperhub.admin.mapper;

import cn.paperhub.admin.vo.OperationLogView;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OperationLogMapper {
    @Select("<script>SELECT id,user_name,action,target,result,time FROM operation_log "+
            "<if test='action != &quot;all&quot;'>WHERE action=#{action} </if> ORDER BY time DESC,id DESC</script>")
    List<OperationLogView> list(@Param("action") String action);
}
