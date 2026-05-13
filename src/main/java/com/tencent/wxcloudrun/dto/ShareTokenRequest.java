package com.tencent.wxcloudrun.dto;

import lombok.Data;

@Data
public class ShareTokenRequest {
  private String userKey;
  private String shareToken;
}
