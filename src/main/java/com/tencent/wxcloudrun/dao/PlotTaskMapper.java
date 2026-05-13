package com.tencent.wxcloudrun.dao;

import com.tencent.wxcloudrun.model.PlotTask;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PlotTaskMapper {
  @Insert("INSERT INTO plot_tasks(user_id, access_id, upload_file_id, plot_type, output_format, status, progress, options_json, parse_summary) VALUES(#{userId}, #{accessId}, #{uploadFileId}, #{plotType}, #{outputFormat}, #{status}, #{progress}, #{optionsJson}, #{parseSummary})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  void insert(PlotTask task);

  @Update("UPDATE plot_tasks SET upload_file_id=#{uploadFileId}, parse_summary=#{parseSummary}, updated_at=NOW() WHERE id=#{id}")
  void attachUpload(PlotTask task);

  @Select("SELECT * FROM plot_tasks WHERE id=#{id}")
  PlotTask findById(@Param("id") Long id);

  @Select("SELECT * FROM plot_tasks WHERE user_id=#{userId} ORDER BY created_at DESC LIMIT #{limit}")
  List<PlotTask> findByUser(@Param("userId") Long userId, @Param("limit") Integer limit);

  @Update("UPDATE plot_tasks SET status='RUNNING', progress=10, started_at=NOW(), updated_at=NOW() WHERE id=#{id}")
  void markRunning(@Param("id") Long id);

  @Update("UPDATE plot_tasks SET progress=#{progress}, updated_at=NOW() WHERE id=#{id}")
  void updateProgress(@Param("id") Long id, @Param("progress") Integer progress);

  @Update("UPDATE plot_tasks SET status='SUCCESS', progress=100, finished_at=NOW(), updated_at=NOW() WHERE id=#{id}")
  void markSuccess(@Param("id") Long id);

  @Update("UPDATE plot_tasks SET status='FAILED', progress=100, error_message=#{errorMessage}, finished_at=NOW(), updated_at=NOW() WHERE id=#{id}")
  void markFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage);
}
