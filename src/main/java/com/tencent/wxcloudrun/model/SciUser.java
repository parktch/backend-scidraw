package com.tencent.wxcloudrun.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SciUser {
  private Long id;
  private String userKey;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
