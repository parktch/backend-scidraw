package com.tencent.wxcloudrun;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import com.tencent.wxcloudrun.config.SciDrawProperties;

@SpringBootApplication
@MapperScan(basePackages = {"com.tencent.wxcloudrun.dao"})
@EnableAsync
@EnableConfigurationProperties(SciDrawProperties.class)
public class WxCloudRunApplication {  

  public static void main(String[] args) {
    SpringApplication.run(WxCloudRunApplication.class, args);
  }
}
