package com.tencent.wxcloudrun.dto;

import lombok.Data;

@Data
public class RedeemCouponRequest {
  private String userKey;
  private String couponCode;
}
