package com.tencent.wxcloudrun.dto;

import lombok.Data;

@Data
public class CreateTaskFromUploadRequest {
  private String userKey;
  private String accessToken;
  private Long uploadId;
  private String plotType;
  private String outputFormat;
  private String options;
}
