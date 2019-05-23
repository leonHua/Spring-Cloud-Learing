package cn.leon.leonconsumer.mapper;

import cn.leon.leonconsumer.config.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    public User getDmUserById(@Param(value = "id") long id) throws Exception;
}
