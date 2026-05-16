package com.tencent.wxcloudrun.service;

import com.tencent.wxcloudrun.dto.AccessResponse;
import com.tencent.wxcloudrun.dto.CreateTaskResponse;
import com.tencent.wxcloudrun.dto.TaskDetailResponse;
import com.tencent.wxcloudrun.dto.UploadFileResponse;
import com.tencent.wxcloudrun.model.PlotResultResource;
import com.tencent.wxcloudrun.model.PlotTask;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SciDrawService {
  AccessResponse redeemCoupon(String userKey, String couponCode);
  AccessResponse redeemShareToken(String userKey, String shareToken);
  UploadFileResponse uploadFile(String userKey, MultipartFile file);
  CreateTaskResponse createTaskFromUpload(String userKey, String accessToken, Long uploadId, String plotType, String outputFormat, String optionsJson);
  CreateTaskResponse createTask(String userKey, String accessToken, MultipartFile file, String plotType, String outputFormat, String optionsJson);
  TaskDetailResponse getTask(Long taskId);
  List<PlotTask> getHistory(String userKey, Integer limit);
  PlotResultResource getResult(Long resourceId);
  Resource loadResultResource(PlotResultResource result);
}
