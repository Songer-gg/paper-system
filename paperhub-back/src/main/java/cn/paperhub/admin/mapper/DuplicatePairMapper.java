package cn.paperhub.admin.mapper;

import cn.paperhub.admin.entity.DuplicatePairEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DuplicatePairMapper extends BaseMapper<DuplicatePairEntity> {
    @Select("SELECT * FROM duplicate_pair WHERE id=#{id} FOR UPDATE")
    DuplicatePairEntity lock(@Param("id") long id);

    @Update("UPDATE duplicate_pair SET status=#{status},handler_id=#{actor},handler_note=#{note},handled_at=#{time} "+
            "WHERE id=#{id} AND status='pending'")
    int resolve(@Param("id") long id,@Param("status") String status,@Param("actor") long actor,
                @Param("note") String note,@Param("time") LocalDateTime time);
}

