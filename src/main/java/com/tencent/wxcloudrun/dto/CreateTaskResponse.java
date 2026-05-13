package com.tencent.wxcloudrun.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateTaskResponse {
  private Long taskId;
  private String status;
  private Object parseSummary;
}
