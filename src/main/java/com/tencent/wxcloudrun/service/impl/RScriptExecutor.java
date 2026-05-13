package com.tencent.wxcloudrun.service.impl;

import com.tencent.wxcloudrun.config.SciDrawProperties;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RScriptExecutor {
  private final SciDrawProperties properties;

  public RScriptExecutor(SciDrawProperties properties) {
    this.properties = properties;
  }

  public void run(String plotType, Path input, Path output, Path options, Path workDir) throws IOException, InterruptedException {
    String script = properties.getScripts().get(plotType);
    if (script == null || script.trim().isEmpty()) {
      script = properties.getScripts().get("volcano");
    }
    List<String> command = Arrays.asList(
        properties.getRscriptBinary(),
        resolveScript(script),
        "--input", input.toString(),
        "--output", output.toString(),
        "--options", options.toString()
    );
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(workDir.toFile());
    Process process = builder.start();
    boolean finished = process.waitFor(properties.getRscriptTimeoutSeconds(), TimeUnit.SECONDS);
    String stdout = read(process.getInputStream());
    String stderr = read(process.getErrorStream());
    if (!finished) {
      process.destroyForcibly();
      throw new IllegalStateException("R 脚本执行超时");
    }
    if (process.exitValue() != 0) {
      String message = stderr.trim().isEmpty() ? stdout : stderr;
      throw new IllegalStateException(trimMessage(message, "R 脚本执行失败"));
    }
  }

  private String resolveScript(String script) {
    Path path = Paths.get(script);
    if (path.isAbsolute()) {
      return path.toString();
    }
    return Paths.get(System.getProperty("user.dir"), script).toString();
  }

  private String read(InputStream inputStream) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int len;
    while ((len = inputStream.read(buffer)) >= 0) {
      output.write(buffer, 0, len);
    }
    return new String(output.toByteArray(), StandardCharsets.UTF_8);
  }

  private String trimMessage(String message, String fallback) {
    if (message == null || message.trim().isEmpty()) {
      return fallback;
    }
    return message.length() > 1000 ? message.substring(0, 1000) : message;
  }
}
