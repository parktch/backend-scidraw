package com.tencent.wxcloudrun.dao;

import com.tencent.wxcloudrun.model.CouponCode;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CouponCodeMapper {
  @Select("SELECT * FROM coupon_codes WHERE code=#{code} LIMIT 1")
  CouponCode findByCode(@Param("code") String code);

  @Update("UPDATE coupon_codes SET redeemed_times=redeemed_times+1 WHERE id=#{id} AND status='ACTIVE' AND redeemed_times < 1 AND (expires_at IS NULL OR expires_at > NOW())")
  int consumeRedeem(@Param("id") Long id);
}
