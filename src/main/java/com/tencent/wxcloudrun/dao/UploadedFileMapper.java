package com.tencent.wxcloudrun.dao;

import com.tencent.wxcloudrun.model.UploadedFile;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UploadedFileMapper {
  @Insert("INSERT INTO uploaded_files(user_id, task_id, original_name, extension, mime_type, size_bytes, storage_path, normalized_path, parse_status, parse_summary, error_message) VALUES(#{userId}, #{taskId}, #{originalName}, #{extension}, #{mimeType}, #{sizeBytes}, #{storagePath}, #{normalizedPath}, #{parseStatus}, #{parseSummary}, #{errorMessage})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  void insert(UploadedFile file);

  @Select("SELECT * FROM uploaded_files WHERE id=#{id}")
  UploadedFile findById(@Param("id") Long id);
}
