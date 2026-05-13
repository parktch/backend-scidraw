package com.tencent.wxcloudrun.service.impl;

import com.tencent.wxcloudrun.service.FileParseResult;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class FileParserService {
  public FileParseResult parse(Path input, String extension, Path normalizedCsv) throws IOException {
    List<List<String>> rows;
    if ("xlsx".equals(extension)) {
      rows = readXlsx(input);
    } else {
      rows = readText(input);
    }
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("文件为空或没有可解析的数据行");
    }
    int maxColumns = 0;
    for (List<String> row : rows) {
      maxColumns = Math.max(maxColumns, row.size());
    }
    if (maxColumns == 0) {
      throw new IllegalArgumentException("文件没有可识别的字段");
    }
    writeCsv(rows, normalizedCsv);

    List<String> fields = new ArrayList<String>();
    for (int i = 0; i < maxColumns; i++) {
      String value = rows.get(0).size() > i ? rows.get(0).get(i) : "";
      fields.add(value == null || value.trim().isEmpty() ? "column_" + (i + 1) : value.trim());
    }
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("fields", fields);
    summary.put("rows", Math.max(rows.size() - 1, 0));
    summary.put("columns", maxColumns);
    summary.put("detectedDelimiter", "xlsx".equals(extension) ? "xlsx" : detectDelimiter(rows.get(0)));
    summary.put("normalizedFile", normalizedCsv.toString());

    FileParseResult result = new FileParseResult();
    result.setRows(rows);
    result.setSummary(summary);
    result.setNormalizedPath(normalizedCsv);
    return result;
  }

  private List<List<String>> readText(Path input) throws IOException {
    List<List<String>> rows = new ArrayList<List<String>>();
    try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.trim().isEmpty()) {
          rows.add(splitLine(line));
        }
      }
    }
    return rows;
  }

  private List<String> splitLine(String line) {
    String delimiter = line.indexOf('\t') >= 0 ? "\t" : ",";
    String[] parts = line.split(delimiter, -1);
    List<String> values = new ArrayList<String>();
    for (String part : parts) {
      values.add(part.trim());
    }
    return values;
  }

  private String detectDelimiter(List<String> firstRow) {
    return firstRow.size() > 1 ? "comma_or_tab" : "single_column";
  }

  private List<List<String>> readXlsx(Path input) throws IOException {
    List<List<String>> rows = new ArrayList<List<String>>();
    try (Workbook workbook = WorkbookFactory.create(input.toFile())) {
      Sheet sheet = workbook.getSheetAt(0);
      DataFormatter formatter = new DataFormatter();
      for (Row row : sheet) {
        List<String> values = new ArrayList<String>();
        short last = row.getLastCellNum();
        for (int i = 0; i < last; i++) {
          Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
          values.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
        }
        if (!values.isEmpty()) {
          rows.add(values);
        }
      }
    }
    return rows;
  }

  private void writeCsv(List<List<String>> rows, Path output) throws IOException {
    try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
      for (List<String> row : rows) {
        List<String> escaped = new ArrayList<String>();
        for (String value : row) {
          String safe = value == null ? "" : value.replace("\"", "\"\"");
          escaped.add("\"" + safe + "\"");
        }
        writer.write(String.join(",", escaped));
        writer.newLine();
      }
    }
  }
}
