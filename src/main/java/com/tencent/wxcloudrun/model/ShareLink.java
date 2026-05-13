package com.tencent.wxcloudrun.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ShareLink {
  private Long id;
  private String shareToken;
  private Integer grantTimes;
  private Integer maxRedeems;
  private Integer redeemedTimes;
  private LocalDateTime expiresAt;
  private String status;
}
