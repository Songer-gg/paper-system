package cn.paperhub.reading.mapper;

import cn.paperhub.reading.entity.ReadingEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ReadingMapper extends BaseMapper<ReadingEntity> {
    @Update("UPDATE reading_record SET status=#{status},last_read_at=#{time} WHERE id=#{id} AND user_id=#{owner}")
    int status(@Param("id") long id,@Param("owner") long owner,@Param("status") String status,@Param("time") LocalDateTime time);
}

