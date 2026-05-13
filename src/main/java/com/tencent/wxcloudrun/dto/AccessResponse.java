package com.tencent.wxcloudrun.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AccessResponse {
  private String accessToken;
  private Integer totalTimes;
  private Integer remainingTimes;
  private LocalDateTime expiresAt;
}
