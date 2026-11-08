package cn.paperhub.profile.mapper;

import cn.paperhub.auth.entity.UserAccount;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AccountWriteMapper {
    @Select("SELECT * FROM `user` WHERE id=#{id} FOR UPDATE")
    UserAccount lock(@Param("id") long id);

    // All administrator account mutations share this order, including cross-disable requests.
    @Select("SELECT * FROM `user` FORCE INDEX(PRIMARY) WHERE role='admin' ORDER BY id FOR UPDATE")
    List<UserAccount> lockAdministrators();

    @Update("UPDATE `user` SET name=#{name},email=#{email} WHERE id=#{id}")
    int profile(@Param("id") long id,@Param("name") String name,@Param("email") String email);

    @Update("UPDATE `user` SET status=#{status},token_version=token_version+1 WHERE id=#{id}")
    int status(@Param("id") long id,@Param("status") String status);

    @Update("UPDATE `user` SET password=#{hash},token_version=token_version+1 WHERE id=#{id}")
    int password(@Param("id") long id,@Param("hash") String hash);

    @Update("UPDATE `user` SET password=#{hash},token_version=token_version+1,password_reset_at=#{time} WHERE id=#{id}")
    int reset(@Param("id") long id,@Param("hash") String hash,@Param("time") LocalDateTime time);
}
