package com.tencent.wxcloudrun.service.impl;

import com.tencent.wxcloudrun.config.SciDrawProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class LocalStorageService {
  private final SciDrawProperties properties;

  public LocalStorageService(SciDrawProperties properties) {
    this.properties = properties;
  }

  public Path uploadPath(Long userId, Long taskId, String filename) throws IOException {
    Path dir = Paths.get(properties.getStorageRoot(), "uploads", String.valueOf(userId), String.valueOf(taskId));
    Files.createDirectories(dir);
    return dir.resolve(filename);
  }

  public Path uploadPath(Long userId, String uploadKey, String filename) throws IOException {
    Path dir = Paths.get(properties.getStorageRoot(), "uploads", String.valueOf(userId), uploadKey);
    Files.createDirectories(dir);
    return dir.resolve(filename);
  }

  public Path taskPath(Long taskId, String filename) throws IOException {
    Path dir = Paths.get(properties.getStorageRoot(), "tasks", String.valueOf(taskId));
    Files.createDirectories(dir);
    return dir.resolve(filename);
  }

  public Path resultPath(Long taskId, String filename) throws IOException {
    Path dir = Paths.get(properties.getStorageRoot(), "results", String.valueOf(taskId));
    Files.createDirectories(dir);
    return dir.resolve(filename);
  }
}
