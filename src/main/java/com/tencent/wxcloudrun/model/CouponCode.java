package com.tencent.wxcloudrun.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CouponCode {
  private Long id;
  private String code;
  private Integer totalTimes;
  private Integer redeemedTimes;
  private LocalDateTime expiresAt;
  private String status;
}
