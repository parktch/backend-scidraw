package com.tencent.wxcloudrun.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencent.wxcloudrun.config.SciDrawProperties;
import com.tencent.wxcloudrun.dao.*;
import com.tencent.wxcloudrun.dto.AccessResponse;
import com.tencent.wxcloudrun.dto.CreateTaskResponse;
import com.tencent.wxcloudrun.dto.TaskDetailResponse;
import com.tencent.wxcloudrun.model.*;
import com.tencent.wxcloudrun.service.FileParseResult;
import com.tencent.wxcloudrun.service.SciDrawService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SciDrawServiceImpl implements SciDrawService {
  private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<String>(Arrays.asList("txt", "csv", "xlsx"));
  private static final Set<String> SUPPORTED_PLOT_TYPES = new HashSet<String>(Arrays.asList("volcano", "heatmap", "survival", "boxplot"));
  private static final Set<String> SUPPORTED_OUTPUT_FORMATS = new HashSet<String>(Collections.singletonList("png"));

  private final SciUserMapper userMapper;
  private final CouponCodeMapper couponCodeMapper;
  private final ShareLinkMapper shareLinkMapper;
  private final PlotAccessMapper accessMapper;
  private final UploadedFileMapper uploadedFileMapper;
  private final PlotTaskMapper taskMapper;
  private final PlotResultResourceMapper resultMapper;
  private final SciDrawProperties properties;
  private final LocalStorageService storageService;
  private final FileParserService fileParserService;
  private final PlotTaskRunner taskRunner;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public SciDrawServiceImpl(SciUserMapper userMapper, CouponCodeMapper couponCodeMapper, ShareLinkMapper shareLinkMapper,
                            PlotAccessMapper accessMapper, UploadedFileMapper uploadedFileMapper, PlotTaskMapper taskMapper,
                            PlotResultResourceMapper resultMapper, SciDrawProperties properties, LocalStorageService storageService,
                            FileParserService fileParserService, PlotTaskRunner taskRunner) {
    this.userMapper = userMapper;
    this.couponCodeMapper = couponCodeMapper;
    this.shareLinkMapper = shareLinkMapper;
    this.accessMapper = accessMapper;
    this.uploadedFileMapper = uploadedFileMapper;
    this.taskMapper = taskMapper;
    this.resultMapper = resultMapper;
    this.properties = properties;
    this.storageService = storageService;
    this.fileParserService = fileParserService;
    this.taskRunner = taskRunner;
  }

  @Override
  @Transactional
  public AccessResponse redeemCoupon(String userKey, String couponCode) {
    SciUser user = ensureUser(userKey);
    CouponCode coupon = couponCodeMapper.findByCode(required(couponCode, "券码不能为空"));
    if (coupon == null || couponCodeMapper.consumeRedeem(coupon.getId()) != 1) {
      throw new IllegalArgumentException("券码无效、已过期或已被兑换完");
    }
    return createAccess(user.getId(), "COUPON", coupon.getId(), coupon.getTotalTimes(), coupon.getExpiresAt());
  }

  @Override
  @Transactional
  public AccessResponse redeemShareToken(String userKey, String shareToken) {
    SciUser user = ensureUser(userKey);
    ShareLink shareLink = shareLinkMapper.findByToken(required(shareToken, "分享链接无效"));
    if (shareLink == null || shareLinkMapper.consumeRedeem(shareLink.getId()) != 1) {
      throw new IllegalArgumentException("分享链接无效、已过期或已达到兑换上限");
    }
    return createAccess(user.getId(), "SHARE", shareLink.getId(), shareLink.getGrantTimes(), shareLink.getExpiresAt());
  }

  @Override
  @Transactional
  public CreateTaskResponse createTask(String userKey, String accessToken, MultipartFile file, String plotType, String outputFormat, String optionsJson) {
    try {
      SciUser user = ensureUser(userKey);
      PlotAccess access = accessMapper.findByToken(required(accessToken, "accessToken 不能为空"));
      if (access == null || !access.getUserId().equals(user.getId())) {
        throw new IllegalArgumentException("作图权益无效");
      }
      validateUpload(file);
      if (accessMapper.consumeOne(accessToken, user.getId()) != 1) {
        throw new IllegalArgumentException("作图权益已过期或剩余次数不足");
      }

      String ext = extension(file.getOriginalFilename());
      PlotTask task = new PlotTask();
      task.setUserId(user.getId());
      task.setAccessId(access.getId());
      task.setPlotType(normalizePlotType(plotType));
      task.setOutputFormat(normalizeOutputFormat(outputFormat));
      task.setStatus("PENDING");
      task.setProgress(0);
      task.setOptionsJson(normalizeOptions(optionsJson));
      taskMapper.insert(task);

      Path inputPath = storageService.uploadPath(user.getId(), task.getId(), "input." + ext);
      file.transferTo(inputPath.toFile());
      Path normalizedPath = storageService.uploadPath(user.getId(), task.getId(), "input.normalized.csv");
      FileParseResult parseResult = fileParserService.parse(inputPath, ext, normalizedPath);
      String summaryJson = objectMapper.writeValueAsString(parseResult.getSummary());

      UploadedFile upload = new UploadedFile();
      upload.setUserId(user.getId());
      upload.setTaskId(task.getId());
      upload.setOriginalName(file.getOriginalFilename());
      upload.setExtension(ext);
      upload.setMimeType(file.getContentType());
      upload.setSizeBytes(file.getSize());
      upload.setStoragePath(inputPath.toString());
      upload.setNormalizedPath(normalizedPath.toString());
      upload.setParseStatus("SUCCESS");
      upload.setParseSummary(summaryJson);
      uploadedFileMapper.insert(upload);

      task.setUploadFileId(upload.getId());
      task.setParseSummary(summaryJson);
      taskMapper.attachUpload(task);
      scheduleTask(task.getId());
      return new CreateTaskResponse(task.getId(), "PENDING", parseResult.getSummary());
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException(ex.getMessage(), ex);
    }
  }

  @Override
  public TaskDetailResponse getTask(Long taskId) {
    PlotTask task = taskMapper.findById(taskId);
    if (task == null) {
      throw new IllegalArgumentException("任务不存在");
    }
    return new TaskDetailResponse(task, resultMapper.findByTaskId(taskId));
  }

  @Override
  public List<PlotTask> getHistory(String userKey, Integer limit) {
    SciUser user = userMapper.findByUserKey(required(userKey, "userKey 不能为空"));
    if (user == null) {
      return new ArrayList<PlotTask>();
    }
    int safeLimit = limit == null || limit <= 0 || limit > 100 ? 20 : limit;
    return taskMapper.findByUser(user.getId(), safeLimit);
  }

  @Override
  public PlotResultResource getResult(Long resourceId) {
    PlotResultResource result = resultMapper.findById(resourceId);
    if (result == null) {
      throw new IllegalArgumentException("结果资源不存在");
    }
    return result;
  }

  @Override
  public Resource loadResultResource(PlotResultResource result) {
    return new FileSystemResource(result.getStoragePath());
  }

  private AccessResponse createAccess(Long userId, String sourceType, Long sourceId, Integer times, LocalDateTime expiresAt) {
    int grantTimes = times == null || times <= 0 ? 1 : times;
    PlotAccess access = new PlotAccess();
    access.setUserId(userId);
    access.setAccessToken(UUID.randomUUID().toString().replace("-", ""));
    access.setSourceType(sourceType);
    access.setSourceId(sourceId);
    access.setTotalTimes(grantTimes);
    access.setRemainingTimes(grantTimes);
    access.setExpiresAt(expiresAt);
    access.setStatus("ACTIVE");
    accessMapper.insert(access);
    return new AccessResponse(access.getAccessToken(), grantTimes, grantTimes, expiresAt);
  }

  private SciUser ensureUser(String userKey) {
    String key = required(userKey, "userKey 不能为空");
    SciUser user = userMapper.findByUserKey(key);
    if (user != null) {
      return user;
    }
    user = new SciUser();
    user.setUserKey(key);
    userMapper.insert(user);
    return user;
  }

  private void validateUpload(MultipartFile file) throws Exception {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("上传文件为空");
    }
    if (file.getSize() > properties.getMaxUploadSizeBytes()) {
      throw new IllegalArgumentException("文件超过大小限制");
    }
    String ext = extension(file.getOriginalFilename());
    if (!ALLOWED_EXTENSIONS.contains(ext)) {
      throw new IllegalArgumentException("仅支持 TXT、CSV、XLSX 文件");
    }
    String contentType = file.getContentType();
    if (contentType != null && contentType.toLowerCase().contains("image")) {
      throw new IllegalArgumentException("文件 MIME 类型不正确");
    }
  }

  private String extension(String filename) {
    if (filename == null || filename.lastIndexOf('.') < 0) {
      throw new IllegalArgumentException("文件缺少扩展名");
    }
    return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
  }

  private String required(String value, String message) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private String emptyToDefault(String value, String defaultValue) {
    return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
  }

  private String normalizeOptions(String optionsJson) throws Exception {
    String options = emptyToDefault(optionsJson, "{}");
    objectMapper.readTree(options);
    return options;
  }

  private String normalizePlotType(String plotType) {
    String value = emptyToDefault(plotType, "volcano").toLowerCase(Locale.ROOT);
    if (!SUPPORTED_PLOT_TYPES.contains(value)) {
      throw new IllegalArgumentException("暂不支持该作图类型");
    }
    return value;
  }

  private String normalizeOutputFormat(String outputFormat) {
    String value = emptyToDefault(outputFormat, "png").toLowerCase(Locale.ROOT);
    if (!SUPPORTED_OUTPUT_FORMATS.contains(value)) {
      throw new IllegalArgumentException("暂仅支持 PNG 输出");
    }
    return value;
  }

  private void scheduleTask(final Long taskId) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          taskRunner.run(taskId);
        }
      });
    } else {
      taskRunner.run(taskId);
    }
  }
}
