package com.tencent.wxcloudrun.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PlotTask {
  private Long id;
  private Long userId;
  private Long accessId;
  private Long uploadFileId;
  private String plotType;
  private String outputFormat;
  private String status;
  private Integer progress;
  private String optionsJson;
  private String parseSummary;
  private String errorMessage;
  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
