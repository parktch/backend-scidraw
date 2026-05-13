package com.tencent.wxcloudrun.service;

import lombok.Data;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Data
public class FileParseResult {
  private Path normalizedPath;
  private Map<String, Object> summary;
  private List<List<String>> rows;
}
