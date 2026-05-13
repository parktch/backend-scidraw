package com.tencent.wxcloudrun.dao;

import com.tencent.wxcloudrun.model.PlotAccess;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PlotAccessMapper {
  @Insert("INSERT INTO plot_access(user_id, access_token, source_type, source_id, total_times, remaining_times, expires_at, status) VALUES(#{userId}, #{accessToken}, #{sourceType}, #{sourceId}, #{totalTimes}, #{remainingTimes}, #{expiresAt}, #{status})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  void insert(PlotAccess access);

  @Select("SELECT * FROM plot_access WHERE access_token=#{accessToken} LIMIT 1")
  PlotAccess findByToken(@Param("accessToken") String accessToken);

  @Update("UPDATE plot_access SET remaining_times=remaining_times-1 WHERE access_token=#{accessToken} AND user_id=#{userId} AND status='ACTIVE' AND remaining_times > 0 AND (expires_at IS NULL OR expires_at > NOW())")
  int consumeOne(@Param("accessToken") String accessToken, @Param("userId") Long userId);
}
