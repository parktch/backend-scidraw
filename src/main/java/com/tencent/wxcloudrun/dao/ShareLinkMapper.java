package com.tencent.wxcloudrun.dao;

import com.tencent.wxcloudrun.model.ShareLink;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ShareLinkMapper {
  @Select("SELECT * FROM share_links WHERE share_token=#{shareToken} LIMIT 1")
  ShareLink findByToken(@Param("shareToken") String shareToken);

  @Update("UPDATE share_links SET redeemed_times=redeemed_times+1 WHERE id=#{id} AND status='ACTIVE' AND redeemed_times < max_redeems AND (expires_at IS NULL OR expires_at > NOW())")
  int consumeRedeem(@Param("id") Long id);
}
