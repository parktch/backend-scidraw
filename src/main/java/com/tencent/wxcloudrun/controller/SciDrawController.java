package com.tencent.wxcloudrun.controller;

import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.dto.CreateTaskFromUploadRequest;
import com.tencent.wxcloudrun.dto.RedeemCouponRequest;
import com.tencent.wxcloudrun.dto.ShareTokenRequest;
import com.tencent.wxcloudrun.model.PlotResultResource;
import com.tencent.wxcloudrun.service.SciDrawService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/plot")
public class SciDrawController {
  private final SciDrawService sciDrawService;

  public SciDrawController(SciDrawService sciDrawService) {
    this.sciDrawService = sciDrawService;
  }

  @PostMapping("/coupons/redeem")
  public ApiResponse redeemCoupon(@RequestBody RedeemCouponRequest request) {
    return ApiResponse.ok(sciDrawService.redeemCoupon(request.getUserKey(), request.getCouponCode()));
  }

  @PostMapping("/share/validate")
  public ApiResponse redeemShareToken(@RequestBody ShareTokenRequest request) {
    return ApiResponse.ok(sciDrawService.redeemShareToken(request.getUserKey(), request.getShareToken()));
  }

  @PostMapping("/tasks")
  public ApiResponse createTask(@RequestParam("userKey") String userKey,
                                @RequestParam("accessToken") String accessToken,
                                @RequestParam(value = "plotType", required = false) String plotType,
                                @RequestParam(value = "outputFormat", required = false) String outputFormat,
                                @RequestParam(value = "options", required = false) String optionsJson,
                                @RequestPart("file") MultipartFile file) {
    return ApiResponse.ok(sciDrawService.createTask(userKey, accessToken, file, plotType, outputFormat, optionsJson));
  }

  @PostMapping("/uploads")
  public ApiResponse uploadFile(@RequestParam("userKey") String userKey,
                                @RequestPart("file") MultipartFile file) {
    return ApiResponse.ok(sciDrawService.uploadFile(userKey, file));
  }

  @PostMapping("/tasks/from-upload")
  public ApiResponse createTaskFromUpload(@RequestBody CreateTaskFromUploadRequest request) {
    return ApiResponse.ok(sciDrawService.createTaskFromUpload(
        request.getUserKey(),
        request.getAccessToken(),
        request.getUploadId(),
        request.getPlotType(),
        request.getOutputFormat(),
        request.getOptions()));
  }

  @GetMapping("/tasks/{taskId}")
  public ApiResponse getTask(@PathVariable("taskId") Long taskId) {
    return ApiResponse.ok(sciDrawService.getTask(taskId));
  }

  @GetMapping("/tasks")
  public ApiResponse history(@RequestParam("userKey") String userKey,
                             @RequestParam(value = "limit", required = false) Integer limit) {
    return ApiResponse.ok(sciDrawService.getHistory(userKey, limit));
  }

  @GetMapping("/resources/{resourceId}")
  public ResponseEntity<Resource> getResource(@PathVariable("resourceId") Long resourceId) {
    PlotResultResource result = sciDrawService.getResult(resourceId);
    Resource resource = sciDrawService.loadResultResource(result);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"result." + result.getFormat() + "\"")
        .body(resource);
  }

  @ExceptionHandler(Exception.class)
  public ApiResponse handle(Exception ex) {
    String message = ex.getMessage() == null ? "服务处理失败" : ex.getMessage();
    return ApiResponse.error(message);
  }
}
