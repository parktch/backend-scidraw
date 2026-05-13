package com.tencent.wxcloudrun.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UploadedFile {
  private Long id;
  private Long userId;
  private Long taskId;
  private String originalName;
  private String extension;
  private String mimeType;
  private Long sizeBytes;
  private String storagePath;
  private String normalizedPath;
  private String parseStatus;
  private String parseSummary;
  private String errorMessage;
  private LocalDateTime createdAt;
}
