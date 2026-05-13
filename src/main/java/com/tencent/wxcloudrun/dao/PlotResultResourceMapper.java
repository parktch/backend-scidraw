package com.tencent.wxcloudrun.dao;

import com.tencent.wxcloudrun.model.PlotResultResource;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PlotResultResourceMapper {
  @Insert("INSERT INTO plot_result_resources(task_id, resource_type, format, storage_path, access_url, size_bytes) VALUES(#{taskId}, #{resourceType}, #{format}, #{storagePath}, #{accessUrl}, #{sizeBytes})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  void insert(PlotResultResource resource);

  @Select("SELECT * FROM plot_result_resources WHERE id=#{id}")
  PlotResultResource findById(@Param("id") Long id);

  @Select("SELECT * FROM plot_result_resources WHERE task_id=#{taskId} ORDER BY id ASC")
  List<PlotResultResource> findByTaskId(@Param("taskId") Long taskId);

  @Update("UPDATE plot_result_resources SET access_url=#{accessUrl} WHERE id=#{id}")
  void updateAccessUrl(PlotResultResource resource);
}
