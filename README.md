# 使用方法

## 启动

启动 Docker Desktop，在项目根目录打开 PowerShell 7。将 DeepSeek API Key 填入 `backend/src/main/resources/application.yaml` 的 `spring.ai.openai.api-key`。使用充值申请时，在项目根目录新建 `config` 文件夹，把自己的收款码存为 `config/recharge-qr.png`。

首次构建需要联网下载依赖和语音模型。运行下列命令后，打开 [本机工作台](http://127.0.0.1:8090)，用手机号和密码创建账号并登录。

```powershell
docker compose up -d --build --wait
```

端口冲突时，在当前 PowerShell 窗口为 `WHZNT_WEB_PORT` 或 `WHZNT_DB_PORT` 指定空闲端口，再启动。

从本机 Java 运行方式切换到容器时，若已有资料原件，先在同一 PowerShell 窗口设置以下变量，再运行启动命令。此前自定义过存储目录的，改填对应的 `originals` 目录。以后重建容器时沿用此设置。

```powershell
$env:WHZNT_LEGACY_ORIGINALS = Join-Path $env:LOCALAPPDATA 'whznt\data\originals'
```

## 写稿

1. 打开「写稿」。有主题时填入主题，选口播或图文，点「按题生成」。需要题目时，点「先看三个方向」或「替我选题并写一篇」。
2. 在稿件区点「编辑」修改文字。选中一段后可润色、缩写或换语气，确认后应用到选区。点「保存版本」写入新版。
3. 从「历史」搜索会话标题。打开稿件后，在版本下拉框选择要看的版本，再点「导出 Markdown」。

## 人物项目

1. 打开「人物项目」，点「新建人物项目」，填姓名或称呼，可以补充简介。
2. 在「资料与版本」添加资料。选好类型后粘贴文字或导入 DOCX、TXT、MD、文字版 PDF。更新原文时在该条资料点「新增版本」。
3. 回到「分步创作」，在需要的步骤点「生成」。手上已有故事、定位或选题时，在对应步骤点「录入已有成果」。粘贴已有选题时每行写一个标题。
4. 在选题下选一条「按题写口播」。生成期间可以离开页面，回来在「生成记录」打开结果。
5. 改稿时点「保存版本」。要查看旧稿用过的资料，先切换版本，再点正文引用或「这个版本的来源」。定位更新后，在对应口播稿旁点「重做这篇」。

## 视频字幕

1. 打开「视频字幕」，上传 H.264 编码的 MP4，有声音时使用 AAC 编码。
2. 选择「按录音识别」「逐字稿对齐」或「导入 SRT」。逐字稿也可以从稿件编辑器点「给这版稿配字幕」带入。
3. 打开已完成的任务，点字幕行跳到相应时间。修改文字或起止时间后，点「保存时间轴」。
4. 点「下载 SRT」，或点「导出带字幕的 MP4」。视频导出后回到同一任务下载成片。

## 充值申请

1. 打开「充值申请」，选择预设金额或输入实际支付金额。
2. 使用页面中的二维码付款，上传 PNG 或 JPG 付款截图，按需填写备注。
3. 点「提交申请」，保存页面显示的申请编号。之后可在「我的申请记录」中查回。

## 停止

在项目根目录运行 `docker compose stop`。下次运行 `docker compose up -d --wait`，可继续使用已有记录。

## 本机调试

使用本机 JDK 17、Maven 和 Node.js。在项目根目录运行 `docker compose up -d database` 和 `npm.cmd ci --prefix .\backend\qmd`。分别在 `backend` 运行 `mvn.cmd spring-boot:run`，在 `frontend` 运行 `npm.cmd ci` 和 `npm.cmd run dev`。调试页为 [127.0.0.1:5173](http://127.0.0.1:5173)。

要在本机调试字幕，将 FFmpeg 和 FFprobe 加入 PATH，在 `backend` 运行 `python -m pip install -r subtitle/requirements.txt` 安装语音依赖。要调试充值，将运行后端的 PowerShell 窗口中的 `WHZNT_RECHARGE_QR_FILE` 设为收款码的绝对路径。退出调试时在前后端终端按 `Ctrl+C`。
