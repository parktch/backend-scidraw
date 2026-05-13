package com.tencent.wxcloudrun.dao;

import com.tencent.wxcloudrun.model.SciUser;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SciUserMapper {
  @Select("SELECT * FROM sci_users WHERE user_key=#{userKey} LIMIT 1")
  SciUser findByUserKey(@Param("userKey") String userKey);

  @Insert("INSERT INTO sci_users(user_key) VALUES(#{userKey})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  void insert(SciUser user);
}
