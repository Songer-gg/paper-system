package cn.paperhub.audit.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AuditMapper {
    @Insert("INSERT INTO operation_log(user_id,user_name,action,target,result,time) " +
            "SELECT id,name,#{action},#{target},#{result},#{time} FROM `user` WHERE id=#{actor}")
    int append(@Param("actor") long actor, @Param("action") String action, @Param("target") String target,
               @Param("result") String result, @Param("time") LocalDateTime time);
}

