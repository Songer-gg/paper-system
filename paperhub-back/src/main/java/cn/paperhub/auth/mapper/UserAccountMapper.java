package cn.paperhub.auth.mapper;

import cn.paperhub.auth.entity.UserAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
    @Select("SELECT COUNT(*) FROM `user` WHERE username IN (#{username},#{email}) OR email IN (#{username},#{email})")
    long countLoginIdentifiers(@Param("username") String username, @Param("email") String email);

    @Select("SELECT id, username, password, name, email, role, status, token_version, registered_at " +
            "FROM `user` WHERE username = #{login} OR email = #{login} LIMIT 2")
    List<UserAccount> findByLogin(@Param("login") String login);
}
