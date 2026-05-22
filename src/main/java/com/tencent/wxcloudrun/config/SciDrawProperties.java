package com.tencent.wxcloudrun.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "scidraw")
public class SciDrawProperties {
  private String storageRoot = "/tmp/scidraw";
  private long maxUploadSizeBytes = 10 * 1024 * 1024;
  private boolean accessControlEnabled = false;
  private String rscriptBinary = "Rscript";
  private long rscriptTimeoutSeconds = 60;
  private Map<String, String> scripts = new HashMap<>();
}
