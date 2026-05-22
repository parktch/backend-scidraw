package com.tencent.wxcloudrun.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResourceBase64Response {
  private Long resourceId;
  private String format;
  private String contentType;
  private String contentBase64;
}
