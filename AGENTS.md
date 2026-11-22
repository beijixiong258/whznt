# AGENTS.md

这个项目整体上的本质是做一个逆向工程，然后再根据细节需求微调。逆向目标是修缘灵犀，原站网址为 <https://app.xiuyuan.ai/>。

## 开发范围

在[功能分析与设计](./功能分析与设计.md#features)查看本站功能，在[业务需求与技术路线](./业务需求与技术路线.md)查看技术选型和验收条件。原站页面和课程材料供对照，具体实现按用户当前要求。新增文体优先加到现有写稿流程。

普通用户使用本站网页工作台，管理员由 `/admin/login` 单独登录并进入 `/admin`。两端通过同源 `/api` 调用同一个后端，Spring Security 分别校验工作台和管理接口所需身份。模型调用、工作流编排、资料检索和字幕处理均由后端按业务流程调度。

《功能分析与设计》用功能名称作索引，保持简洁。《业务需求与技术路线》采用正式书面语，沿用六个二级标题，写清使用路径、技术选型及验收条件。两份文档均省去功能编号、核查日期和重复的来源说明。

- 从零写稿允许主题、人物资料和附件全部为空。人物创作允许从空白或已有故事、定位、选题继续。原始访谈可作事实依据，风格参考只影响表达。
- 保留用户编辑过的稿件及所用上游版本。重做某一步时生成新版。检索、引用、下载文件都由后端校验用户及项目归属。
- 字幕以实际录音为准，可带稿对齐或直接导入 SRT。校对与导出复用时间轴。用户扫码后提交付款截图，管理员核对并手动入账。余额、积分流水和申请状态在同一事务中更新，重复提交或审核保持幂等。

## 工程约定

- `frontend/` 使用 Vue 3、TypeScript、Vite 和腾讯 TDesign Vue Next。工作台和后台面向电脑端，主内容铺满工作区。表单、上传、列表、反馈优先用现成组件。
- `backend/` 使用 Spring Boot 单体，启动类放在 `com.whznt`。入口、业务和数据访问按 `controller → service → mapper` 分层。请求、返回、实体分别放 `dto`、`vo`、`entity`。配置和异常放 `config`、`exception`。图编排、外部接入和持久任务分别放 `workflow`、`integration`、`job`，随实际代码建包。
- MyBatis-Plus 的实体服务保留 `IService<T>` 和 `service.impl` 下的 `ServiceImpl<M, T>`，Mapper 继承 `BaseMapper<T>`。同一实体的操作尽量归入同一 Service。用 Lombok 做构造器注入和日志，通用操作优先使用标准库。
- 文本模型统一由 Spring AI 调用 OpenAI 兼容上游，默认地址为 DeepSeek，型号为 `deepseek-flash`。Compose 从本机忽略目录内的 `config/ai.properties` 读取默认 API Key。管理员保存的上游配置写入后端私有文件卷并即时生效。
- 有依赖的步骤用 LangGraph4j，检查点复用 `langgraph4j-postgres-saver`。业务数据用 PostgreSQL，表结构由 Flyway 管理。QMD 用自己的 SQLite 索引，通过 Spring AI MCP 客户端连接。本地语音识别和对齐可独立选型，字幕合成用 FFmpeg。
- `frontend/Dockerfile` 和 `backend/Dockerfile` 分别构建 `whznt-frontend` 和 `whznt-backend`。Compose 以 Docker 自带的 `bridge` 启动 PostgreSQL 16 Alpine，前后端共用数据库容器的网络。Nginx 同源转发 `/api`，QMD 与字幕工具随后端镜像打包，业务文件和数据库数据放持久卷。

## 工作方式

Windows 命令用 PowerShell 7。先读相关源码和已有包，优先复用。架构调整、跨模块改动、数据迁移及公共接口变更，先说明范围和验收方式并取得确认。

开发阶段按当前数据模型和账号规则实现。旧的本地开发数据可按用户指定范围直接重置，兼容分支一并移除。项目从空库启动时由 Flyway 初始化。

Java 源码按本次明确要求修改。运行 Maven 或 Java、编译、启动、清理 `target`，以及修改 `pom.xml`、`.idea` 或其他项目配置，均按用户针对本次任务的明确授权执行。提交和推送按用户明确要求进行。

## 测试与清理

1. 按本次获准范围测试，从实际受影响的页面或接口验证。账号规则覆盖合法输入、长度边界和拒绝路径，页面改动检查桌面布局，数据库改动验证 Flyway 迁移。需整站联调时，在项目根目录运行 `docker compose up -d --build --wait`，确认服务就绪后再测。前端端口冲突时，用当前终端的 `WHZNT_WEB_PORT` 临时指定空闲端口。
2. 测试账号、稿件和上传文件使用临时素材并记录 ID。临时脚本、截图、日志和构建检查产物放在工作区外的临时目录。
3. 验收结束后按记录清理本轮测试数据和文件并复查。先运行 `docker compose stop --timeout 30 frontend backend`，再运行 `docker compose stop --timeout 30 database`。用 `docker compose ps --all` 确认服务已停止。临时产物移入 `C:\Users\user\Desktop\智能体回收站\垃圾桶`。
4. 提交前核对暂存范围并运行 `git diff --cached --check`，按任务提交源码、依赖清单、锁文件和正式文档。交付时写明实际验证和清理结果。
