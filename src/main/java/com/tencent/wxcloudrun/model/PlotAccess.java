package com.tencent.wxcloudrun.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PlotAccess {
  private Long id;
  private Long userId;
  private String accessToken;
  private String sourceType;
  private Long sourceId;
  private Integer totalTimes;
  private Integer remainingTimes;
  private LocalDateTime expiresAt;
  private String status;
}
