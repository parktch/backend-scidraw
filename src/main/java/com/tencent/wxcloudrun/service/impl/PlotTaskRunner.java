package com.tencent.wxcloudrun.service.impl;

import com.tencent.wxcloudrun.dao.PlotResultResourceMapper;
import com.tencent.wxcloudrun.dao.PlotTaskMapper;
import com.tencent.wxcloudrun.dao.UploadedFileMapper;
import com.tencent.wxcloudrun.model.PlotResultResource;
import com.tencent.wxcloudrun.model.PlotTask;
import com.tencent.wxcloudrun.model.UploadedFile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class PlotTaskRunner {
  private final PlotTaskMapper taskMapper;
  private final UploadedFileMapper uploadedFileMapper;
  private final PlotResultResourceMapper resultMapper;
  private final LocalStorageService storageService;
  private final RScriptExecutor rScriptExecutor;

  public PlotTaskRunner(PlotTaskMapper taskMapper, UploadedFileMapper uploadedFileMapper,
                        PlotResultResourceMapper resultMapper, LocalStorageService storageService,
                        RScriptExecutor rScriptExecutor) {
    this.taskMapper = taskMapper;
    this.uploadedFileMapper = uploadedFileMapper;
    this.resultMapper = resultMapper;
    this.storageService = storageService;
    this.rScriptExecutor = rScriptExecutor;
  }

  @Async("plotTaskExecutor")
  public void run(Long taskId) {
    try {
      taskMapper.markRunning(taskId);
      PlotTask task = taskMapper.findById(taskId);
      UploadedFile upload = uploadedFileMapper.findById(task.getUploadFileId());
      Path optionsPath = storageService.taskPath(taskId, "options.json");
      String options = task.getOptionsJson() == null || task.getOptionsJson().trim().isEmpty() ? "{}" : task.getOptionsJson();
      Files.write(optionsPath, options.getBytes(StandardCharsets.UTF_8));

      taskMapper.updateProgress(taskId, 30);
      Path output = storageService.resultPath(taskId, "result.png");
      Path input = resolveInputPath(task, upload);
      Path workDir = output.getParent();
      rScriptExecutor.run(task.getPlotType(), input, output, optionsPath, workDir);

      if (!Files.exists(output) || Files.size(output) == 0) {
        throw new IllegalStateException("R 脚本未生成有效图片");
      }
      taskMapper.updateProgress(taskId, 90);
      PlotResultResource resource = new PlotResultResource();
      resource.setTaskId(taskId);
      resource.setResourceType("PNG");
      resource.setFormat("png");
      resource.setStoragePath(output.toString());
      resource.setAccessUrl("");
      resource.setSizeBytes(Files.size(output));
      resultMapper.insert(resource);
      resource.setAccessUrl("/api/plot/resources/" + resource.getId());
      resultMapper.updateAccessUrl(resource);
      taskMapper.markSuccess(taskId);
    } catch (Exception ex) {
      taskMapper.markFailed(taskId, readableError(ex));
    }
  }

  private String readableError(Exception ex) {
    String message = ex.getMessage();
    if (message == null || message.trim().isEmpty()) {
      message = "作图任务执行失败";
    }
    return message.length() > 1000 ? message.substring(0, 1000) : message;
  }

  private Path resolveInputPath(PlotTask task, UploadedFile upload) {
    if ("pcr".equalsIgnoreCase(task.getPlotType()) && upload.getStoragePath() != null && !upload.getStoragePath().trim().isEmpty()) {
      return Paths.get(upload.getStoragePath());
    }
    return Paths.get(upload.getNormalizedPath());
  }
}
