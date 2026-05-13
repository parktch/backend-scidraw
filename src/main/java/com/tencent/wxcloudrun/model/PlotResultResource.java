package com.tencent.wxcloudrun.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PlotResultResource {
  private Long id;
  private Long taskId;
  private String resourceType;
  private String format;
  private String storagePath;
  private String accessUrl;
  private Long sizeBytes;
  private LocalDateTime createdAt;
}
