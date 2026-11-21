# AGENTS.md

这个项目整体上的本质是做一个逆向工程，然后再根据细节需求微调。逆向目标是修缘灵犀，原站网址为 <https://app.xiuyuan.ai/>。

## 开发范围

在[功能分析与设计](./功能分析与设计.md#planned)找本次要做的功能，在[业务需求与技术路线](./业务需求与技术路线.md)看技术路线和交付顺序。原站页面和课程材料供对照，具体实现按用户当前要求。新增文体优先加到现有写稿流程，后续候选等用户选定再做。

面向用户的功能限于本站网页工作台。同源 `/api` 供本站网页调用后端。模型调用、工作流编排、资料检索和字幕处理均由后端按业务流程调度。

《功能分析与设计》用功能名称作索引，保持简洁。《业务需求与技术路线》采用正式书面语，沿用六个二级标题，写清使用路径、技术选型及验收条件。两份文档均省去功能编号、核查日期和重复的来源说明。

- 从零写稿允许主题、人物资料和附件全部为空。人物创作允许从已有故事、定位或选题继续。原始访谈可作事实依据，风格参考只影响表达。
- 保留用户编辑过的稿件及所用上游版本。重做某一步时生成新版。检索、引用、下载文件都由后端校验用户及项目归属。
- 字幕以实际录音为准，可带稿对齐或直接导入 SRT。校对与导出复用时间轴。充值做到二维码、付款截图、申请编号和本人记录，重复提交返回同一笔申请。

## 工程约定

- `frontend/` 使用 Vue 3、TypeScript、Vite 和腾讯 TDesign Vue Next。表单、上传、列表、反馈优先用现成组件，桌面和手机共用一套页面。
- `backend/` 使用 Spring Boot 单体，启动类放在 `com.whznt`。入口、业务和数据访问按 `controller → service → mapper` 分层。请求、返回、实体分别放 `dto`、`vo`、`entity`。配置和异常放 `config`、`exception`。图编排、外部接入和持久任务分别放 `workflow`、`integration`、`job`，随实际代码建包。
- MyBatis-Plus 的实体服务保留 `IService<T>` 和 `service.impl` 下的 `ServiceImpl<M, T>`，Mapper 继承 `BaseMapper<T>`。同一实体的操作尽量归入同一 Service。用 Lombok 做构造器注入和日志，通用工具优先复用 Hutool。
- 文本模型统一由 Spring AI 调用 DeepSeek 官方 API，默认使用 `deepseek-flash`。API Key 写在 `backend/src/main/resources/application.yaml` 的 `spring.ai.openai.api-key`，配置随 Spring Boot 应用打包。
- 有依赖的步骤用 LangGraph4j，检查点复用 `langgraph4j-postgres-saver`。业务数据用 PostgreSQL，表结构由 Flyway 管理。QMD 用自己的 SQLite 索引，通过 Spring AI MCP 客户端连接。本地语音识别和对齐可独立选型，字幕合成用 FFmpeg。
- `frontend/Dockerfile` 和 `backend/Dockerfile` 分别构建 `whznt-frontend` 和 `whznt-backend`。根目录 `compose.yaml` 负责服务编排，PostgreSQL 使用官方镜像 `postgres:16-alpine`。Nginx 同源转发 `/api`，QMD 与字幕工具随后端镜像打包，业务文件和数据库数据放持久卷。

## 工作方式

Windows 命令用 PowerShell 7。先读相关源码和已有包，优先复用。架构调整、跨模块改动、数据迁移及公共接口变更，先说明范围和验收方式并取得确认。

开发阶段按当前数据模型和账号规则实现。旧的本地开发数据可按用户指定范围直接重置，兼容分支一并移除。项目从空库启动时由 Flyway 初始化。

Java 源码按本次明确要求修改。运行 Maven 或 Java、编译、启动、清理 `target`，以及修改 `pom.xml`、`.idea` 或其他项目配置，均按用户针对本次任务的明确授权执行。测试按本次获准范围运行，交付时写明实际验证结果。提交和推送按用户明确要求进行。
