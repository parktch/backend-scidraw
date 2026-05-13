package com.tencent.wxcloudrun.dto;

import com.tencent.wxcloudrun.model.PlotResultResource;
import com.tencent.wxcloudrun.model.PlotTask;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TaskDetailResponse {
  private PlotTask task;
  private List<PlotResultResource> results;
}
