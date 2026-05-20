package com.tencent.wxcloudrun.dto;

import lombok.Data;

@Data
public class UploadFileBase64Request {
  private String userKey;
  private String originalName;
  private String contentType;
  private String contentBase64;
}
