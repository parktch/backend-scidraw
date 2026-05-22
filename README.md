# wxcloudrun-springboot
[![GitHub license](https://img.shields.io/github/license/WeixinCloud/wxcloudrun-express)](https://github.com/WeixinCloud/wxcloudrun-express)
![GitHub package.json dependency version (prod)](https://img.shields.io/badge/maven-3.6.0-green)
![GitHub package.json dependency version (prod)](https://img.shields.io/badge/jdk-11-green)

微信云托管 Java Springboot 框架模版，实现简单的计数器读写接口，使用云托管 MySQL 读写、记录计数值。

![](https://qcloudimg.tencent-cloud.cn/raw/be22992d297d1b9a1a5365e606276781.png)


## 快速开始
前往 [微信云托管快速开始页面](https://developers.weixin.qq.com/miniprogram/dev/wxcloudrun/src/basic/guide.html)，选择相应语言的模板，根据引导完成部署。

## 本地调试
下载代码在本地调试，请参考[微信云托管本地调试指南](https://developers.weixin.qq.com/miniprogram/dev/wxcloudrun/src/guide/debug/)。

## 实时开发
代码变动时，不需要重新构建和启动容器，即可查看变动后的效果。请参考[微信云托管实时开发指南](https://developers.weixin.qq.com/miniprogram/dev/wxcloudrun/src/guide/debug/dev.html)

## Dockerfile最佳实践
请参考[如何提高项目构建效率](https://developers.weixin.qq.com/miniprogram/dev/wxcloudrun/src/scene/build/speed.html)

## 目录结构说明
~~~
.
├── Dockerfile                      Dockerfile 文件
├── LICENSE                         LICENSE 文件
├── README.md                       README 文件
├── container.config.json           模板部署「服务设置」初始化配置（二开请忽略）
├── mvnw                            mvnw 文件，处理mevan版本兼容问题
├── mvnw.cmd                        mvnw.cmd 文件，处理mevan版本兼容问题
├── pom.xml                         pom.xml文件
├── settings.xml                    maven 配置文件
├── springboot-cloudbaserun.iml     项目配置文件
└── src                             源码目录
    └── main                        源码主目录
        ├── java                    业务逻辑目录
        └── resources               资源文件目录
~~~


## 服务 API 文档

### 科研绘图后台接口

第一版链路：兑换券码或分享链接得到 `accessToken`，上传 TXT/CSV/XLS/XLSX 解析数据，用户确认后创建异步作图任务，轮询任务状态，成功后用资源 URL 展示 PNG。

#### 初始化数据库

执行 `src/main/resources/db.sql`。脚本会创建用户、券码、分享链接、作图权益、上传文件、作图任务、结果资源等表，并写入演示数据：

- 券码：`SCIDRAW-DEMO`
- 分享 token：`share-demo-token`

#### 兑换券码

`POST /api/plot/coupons/redeem`

```json
{
  "userKey": "wechat-openid-or-dev-user",
  "couponCode": "SCIDRAW-DEMO"
}
```

返回 `accessToken`、总次数、剩余次数和过期时间。

#### 兑换分享链接

`POST /api/plot/share/validate`

```json
{
  "userKey": "wechat-openid-or-dev-user",
  "shareToken": "share-demo-token"
}
```

#### 上传文件并解析

`POST /api/plot/uploads`

表单字段：

- `userKey`：用户标识，第一版可用微信 openid
- `file`：TXT、CSV、XLS、XLSX 文件

返回 `uploadId` 和解析摘要。对于 `1.xls` 这种分块格式，会标准化为 `marker, group, replicate, value`，并返回识别出的指标、分组、缺失值数量等信息。

#### 根据已上传文件创建任务

`POST /api/plot/tasks/from-upload`

```json
{
  "userKey": "wechat-openid-or-dev-user",
  "accessToken": "兑换得到的权益凭证",
  "uploadId": 1,
  "plotType": "boxplot",
  "outputFormat": "png",
  "options": "{\"palette\":\"blue\"}"
}
```

成功后立即返回 `taskId` 和解析摘要，任务状态为 `PENDING`，后台异步执行 R 脚本。

#### 上传文件并创建任务（兼容旧接口）

`POST /api/plot/tasks`

表单字段：

- `userKey`：用户标识，第一版可用微信 openid
- `accessToken`：券码或分享链接兑换得到的权益凭证
- `plotType`：默认 `volcano`，支持小程序中的 `volcano`、`heatmap`、`survival`、`boxplot`、`pcr`
- `outputFormat`：默认 `png`
- `options`：JSON 字符串，默认 `{}`
- `file`：TXT、CSV、XLS、XLSX 文件

成功后立即返回 `taskId` 和解析摘要，任务状态为 `PENDING`，后台异步执行 R 脚本。

#### 查询任务状态

`GET /api/plot/tasks/{taskId}`

返回任务状态、进度、错误信息和结果资源列表。状态包括 `PENDING`、`RUNNING`、`SUCCESS`、`FAILED`。

#### 查询历史记录

`GET /api/plot/tasks?userKey=wechat-openid-or-dev-user&limit=20`

#### 获取图片结果

`GET /api/plot/resources/{resourceId}`

返回 PNG 图片资源。

#### R 脚本配置

默认调用：

```bash
Rscript scripts/plot_volcano.R --input /path/input.normalized.csv --output /path/result.png --options /path/options.json
```

可通过环境变量覆盖：

- `SCIDRAW_STORAGE_ROOT`
- `SCIDRAW_MAX_UPLOAD_SIZE_BYTES`
- `SCIDRAW_RSCRIPT_BINARY`
- `SCIDRAW_RSCRIPT_TIMEOUT_SECONDS`
- `SCIDRAW_SCRIPT_VOLCANO`
- `SCIDRAW_SCRIPT_HEATMAP`
- `SCIDRAW_SCRIPT_SURVIVAL`
- `SCIDRAW_SCRIPT_BOXPLOT`
- `SCIDRAW_SCRIPT_PCR`

PCR 作图入口为 `scripts/plot_pcr.R`，该脚本会调用 `scripts/pcr_charts.mjs` 生成合并 PNG；容器需安装 Node.js 和 `sharp`。

### `GET /api/count`

获取当前计数

#### 请求参数

无

#### 响应结果

- `code`：错误码
- `data`：当前计数值

##### 响应结果示例

```json
{
  "code": 0,
  "data": 42
}
```

#### 调用示例

```
curl https://<云托管服务域名>/api/count
```



### `POST /api/count`

更新计数，自增或者清零

#### 请求参数

- `action`：`string` 类型，枚举值
  - 等于 `"inc"` 时，表示计数加一
  - 等于 `"clear"` 时，表示计数重置（清零）

##### 请求参数示例

```
{
  "action": "inc"
}
```

#### 响应结果

- `code`：错误码
- `data`：当前计数值

##### 响应结果示例

```json
{
  "code": 0,
  "data": 42
}
```

#### 调用示例

```
curl -X POST -H 'content-type: application/json' -d '{"action": "inc"}' https://<云托管服务域名>/api/count
```

## 使用注意
如果不是通过微信云托管控制台部署模板代码，而是自行复制/下载模板代码后，手动新建一个服务并部署，需要在「服务设置」中补全以下环境变量，才可正常使用，否则会引发无法连接数据库，进而导致部署失败。
- MYSQL_ADDRESS
- MYSQL_PASSWORD
- MYSQL_USERNAME
以上三个变量的值请按实际情况填写。如果使用云托管内MySQL，可以在控制台MySQL页面获取相关信息。


## License

[MIT](./LICENSE)
