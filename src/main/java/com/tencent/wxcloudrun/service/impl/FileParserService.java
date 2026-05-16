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
    if ("xls".equals(extension) || "xlsx".equals(extension)) {
      rows = readWorkbook(input);
    } else {
      rows = readText(input);
    }
    rows = trimGrid(rows);
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("文件为空或没有可解析的数据行");
    }

    StandardTable standardTable = toStandardGroupedTable(rows);
    List<List<String>> normalizedRows = standardTable == null ? rows : standardTable.rows;
    int maxColumns = maxColumns(normalizedRows);
    if (maxColumns == 0) {
      throw new IllegalArgumentException("文件没有可识别的字段");
    }
    writeCsv(normalizedRows, normalizedCsv);

    List<String> fields = new ArrayList<String>();
    List<String> firstRow = normalizedRows.get(0);
    for (int i = 0; i < maxColumns; i++) {
      String value = firstRow.size() > i ? firstRow.get(i) : "";
      fields.add(value == null || value.trim().isEmpty() ? "column_" + (i + 1) : value.trim());
    }
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("fields", fields);
    summary.put("rows", Math.max(normalizedRows.size() - 1, 0));
    summary.put("columns", maxColumns);
    summary.put("detectedDelimiter", ("xls".equals(extension) || "xlsx".equals(extension)) ? extension : detectDelimiter(rows.get(0)));
    summary.put("format", standardTable == null ? "table" : "grouped_replicates");
    summary.put("missingValues", countMissing(normalizedRows));
    summary.put("normalizedFile", normalizedCsv.toString());
    if (standardTable != null) {
      summary.put("markers", standardTable.markers);
      summary.put("groups", standardTable.groups);
      summary.put("groupFields", Collections.singletonList("group"));
      summary.put("valueFields", Collections.singletonList("value"));
    }

    FileParseResult result = new FileParseResult();
    result.setRows(normalizedRows);
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

  private List<List<String>> readWorkbook(Path input) throws IOException {
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
        if (!isEmptyRow(values)) {
          rows.add(values);
        }
      }
    }
    return rows;
  }

  private int maxColumns(List<List<String>> rows) {
    int maxColumns = 0;
    for (List<String> row : rows) {
      maxColumns = Math.max(maxColumns, row.size());
    }
    return maxColumns;
  }

  private List<List<String>> trimGrid(List<List<String>> rows) {
    List<List<String>> nonEmptyRows = new ArrayList<List<String>>();
    int firstColumn = Integer.MAX_VALUE;
    int lastColumn = -1;
    for (List<String> row : rows) {
      if (isEmptyRow(row)) {
        continue;
      }
      nonEmptyRows.add(row);
      for (int i = 0; i < row.size(); i++) {
        if (!cell(row, i).isEmpty()) {
          firstColumn = Math.min(firstColumn, i);
          lastColumn = Math.max(lastColumn, i);
        }
      }
    }
    if (nonEmptyRows.isEmpty()) {
      return nonEmptyRows;
    }
    List<List<String>> trimmed = new ArrayList<List<String>>();
    for (List<String> row : nonEmptyRows) {
      List<String> values = new ArrayList<String>();
      for (int i = firstColumn; i <= lastColumn; i++) {
        values.add(cell(row, i));
      }
      trimmed.add(values);
    }
    return trimmed;
  }

  private StandardTable toStandardGroupedTable(List<List<String>> rows) {
    List<List<String>> normalized = new ArrayList<List<String>>();
    Set<String> markers = new LinkedHashSet<String>();
    Set<String> groups = new LinkedHashSet<String>();
    normalized.add(Arrays.asList("marker", "group", "replicate", "value"));

    for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
      List<String> headerRow = rows.get(rowIndex);
      List<Integer> headerColumns = markerHeaderColumns(headerRow);
      if (headerColumns.isEmpty()) {
        continue;
      }
      int groupColumn = headerColumns.get(0) > 0 ? headerColumns.get(0) - 1 : 0;
      for (int headerIndex = 0; headerIndex < headerColumns.size(); headerIndex++) {
        int markerColumn = headerColumns.get(headerIndex);
        int nextMarkerColumn = headerIndex + 1 < headerColumns.size() ? headerColumns.get(headerIndex + 1) : maxColumns(rows);
        String marker = cell(headerRow, markerColumn);
        int dataRows = 0;
        for (int dataRowIndex = rowIndex + 1; dataRowIndex < rows.size(); dataRowIndex++) {
          List<String> dataRow = rows.get(dataRowIndex);
          String group = cell(dataRow, groupColumn);
          if (group.isEmpty() || markerHeaderColumns(dataRow).size() > 0) {
            break;
          }
          int replicate = 1;
          for (int valueColumn = markerColumn; valueColumn < nextMarkerColumn; valueColumn++) {
            String value = cell(dataRow, valueColumn);
            if (isNumeric(value)) {
              normalized.add(Arrays.asList(marker, group, String.valueOf(replicate), value));
              markers.add(marker);
              groups.add(group);
              replicate++;
            }
          }
          if (replicate > 1) {
            dataRows++;
          }
        }
        if (dataRows < 1) {
          normalized.subList(1, normalized.size()).removeIf(row -> marker.equals(row.get(0)));
          markers.remove(marker);
        }
      }
    }

    if (normalized.size() <= 2 || markers.isEmpty() || groups.isEmpty()) {
      return null;
    }
    StandardTable table = new StandardTable();
    table.rows = normalized;
    table.markers = new ArrayList<String>(markers);
    table.groups = new ArrayList<String>(groups);
    return table;
  }

  private List<Integer> markerHeaderColumns(List<String> row) {
    List<Integer> columns = new ArrayList<Integer>();
    for (int i = 0; i < row.size(); i++) {
      String value = cell(row, i);
      if (!value.isEmpty() && !isNumeric(value) && (i == 0 || cell(row, i - 1).isEmpty()) && !isNumeric(cell(row, i + 1))) {
        columns.add(i);
      }
    }
    return columns;
  }

  private boolean isEmptyRow(List<String> row) {
    for (String value : row) {
      if (value != null && !value.trim().isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private String cell(List<String> row, int index) {
    if (index < 0 || index >= row.size() || row.get(index) == null) {
      return "";
    }
    return row.get(index).trim();
  }

  private boolean isNumeric(String value) {
    if (value == null || value.trim().isEmpty()) {
      return false;
    }
    try {
      Double.parseDouble(value.trim());
      return true;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  private int countMissing(List<List<String>> rows) {
    int missing = 0;
    for (List<String> row : rows) {
      for (String value : row) {
        if (value == null || value.trim().isEmpty()) {
          missing++;
        }
      }
    }
    return missing;
  }

  private static class StandardTable {
    private List<List<String>> rows;
    private List<String> markers;
    private List<String> groups;
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
