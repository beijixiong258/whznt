package com.whznt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whznt.dto.SubtitleRequests;
import com.whznt.dto.SubtitleRequests.Cue;
import com.whznt.entity.SubtitleTask;
import com.whznt.entity.SubtitleTask.Mode;
import com.whznt.entity.SubtitleTask.State;
import com.whznt.exception.WorkspaceException;
import com.whznt.integration.PrivateFiles;
import com.whznt.integration.SubtitleMedia;
import com.whznt.integration.SubtitleTimeline;
import com.whznt.mapper.SubtitleTaskMapper;
import com.whznt.service.DraftService;
import com.whznt.service.SubtitleService;
import com.whznt.vo.SubtitleViews;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SubtitleServiceImpl extends ServiceImpl<SubtitleTaskMapper, SubtitleTask> implements SubtitleService {

    private static final long MAX_VIDEO_BYTES = 512L * 1024 * 1024;
    private final PrivateFiles files;
    private final SubtitleMedia media;
    private final SubtitleTimeline timelines;
    private final DraftService drafts;
    private final ObjectMapper json;

    @Override
    public List<SubtitleViews.Summary> tasks(long ownerId) {
        return lambdaQuery().eq(SubtitleTask::getOwnerId, ownerId).orderByDesc(SubtitleTask::getId)
                .list().stream().map(SubtitleViews.Summary::from).toList();
    }

    @Override
    @Transactional
    public SubtitleViews.Detail create(long ownerId, SubtitleRequests.Create request) {
        if (request.video().isEmpty() || request.video().getSize() > MAX_VIDEO_BYTES) {
            throw WorkspaceException.badRequest("请选择不超过 512 MB 的 MP4 视频。");
        }
        Reference reference = reference(ownerId, request);
        boolean imported = request.srt() != null;
        if (imported && !reference.text().isBlank()) {
            throw WorkspaceException.badRequest("请选择导入 SRT 或使用逐字稿对齐。");
        }
        String original = files.saveOriginal(request.video());
        SubtitleMedia.Video video = media.inspect(files.requireOriginal(original));
        if (!imported && !video.audio()) {
            throw WorkspaceException.badRequest("视频没有音轨，可以直接导入 SRT。");
        }
        List<Cue> cues = imported ? timelines.read(request.srt(), video.durationMs()) : List.of();
        SubtitleTask task = new SubtitleTask();
        task.setOwnerId(ownerId);
        task.setSourceKey(original);
        task.setSourceName(sourceName(request.video().getOriginalFilename()));
        task.setDurationMs(video.durationMs());
        task.setMode(imported ? Mode.IMPORT : reference.text().isBlank() ? Mode.AUTO : Mode.ALIGN);
        task.setDraftVersionId(request.draftVersionId());
        task.setReferenceTitle(reference.title());
        task.setReferenceText(reference.text());
        task.setState(imported ? State.READY : State.QUEUED);
        task.setError("");
        task.setOriginalTimeline(encode(cues));
        task.setTimeline(task.getOriginalTimeline());
        task.setVersion(imported ? 1 : 0);
        task.setExportState(State.NONE);
        task.setExportError("");
        task.setCreatedAt(OffsetDateTime.now());
        task.setUpdatedAt(task.getCreatedAt());
        save(task);
        return detail(task);
    }

    @Override
    public SubtitleViews.Detail read(long ownerId, long id) {
        return detail(owned(ownerId, id, false));
    }

    @Override
    @Transactional
    public SubtitleViews.Detail retry(long ownerId, long id) {
        SubtitleTask task = owned(ownerId, id, true);
        if (task.getState() == State.FAILED) {
            task.setState(State.QUEUED);
            task.setError("");
            update(task);
        }
        return detail(task);
    }

    @Override
    @Transactional
    public SubtitleViews.Detail edit(long ownerId, long id, SubtitleRequests.Edit request) {
        SubtitleTask task = owned(ownerId, id, true);
        requireVersion(task, request.version());
        if (task.getExportState().pending()) {
            throw WorkspaceException.conflict("视频正在导出，请等待导出完成后再校对。");
        }
        String timeline = encode(timelines.validate(request.cues(), task.getDurationMs()));
        if (!timeline.equals(task.getTimeline())) {
            task.setTimeline(timeline);
            task.setVersion(task.getVersion() + 1);
            task.setExportState(State.NONE);
            task.setExportError("");
            update(task);
        }
        return detail(task);
    }

    @Override
    @Transactional
    public SubtitleViews.Detail exportVideo(long ownerId, long id, int version) {
        SubtitleTask task = owned(ownerId, id, true);
        requireVersion(task, version);
        if (!task.getExportState().pending() && task.getExportState() != State.READY) {
            task.setExportState(State.QUEUED);
            task.setExportError("");
            update(task);
        }
        return detail(task);
    }

    @Override
    public Resource video(long ownerId, long id) {
        return files.download(owned(ownerId, id, false).getSourceKey());
    }

    @Override
    public SrtDownload downloadSrt(long ownerId, long id, int version) {
        SubtitleTask task = owned(ownerId, id, false);
        requireVersion(task, version);
        return new SrtDownload(stem(task) + ".srt", timelines.render(cues(task)));
    }

    @Override
    public VideoDownload downloadVideo(long ownerId, long id, int version) {
        SubtitleTask task = owned(ownerId, id, false);
        requireVersion(task, version);
        if (task.getExportState() != State.READY) {
            throw WorkspaceException.notFound("当前版本的视频尚未导出。");
        }
        return new VideoDownload(stem(task) + "-字幕.mp4", media.exported(task.getId(), version));
    }

    @Override
    @Transactional
    public void recover() {
        lambdaUpdate().eq(SubtitleTask::getState, State.RUNNING).set(SubtitleTask::getState, State.QUEUED).update();
        lambdaUpdate().eq(SubtitleTask::getExportState, State.RUNNING).set(SubtitleTask::getExportState, State.QUEUED)
                .update();
    }

    @Override
    @Transactional
    public SubtitleTask claimNext() {
        SubtitleTask task = lambdaQuery().eq(SubtitleTask::getState, State.QUEUED)
                .or().eq(SubtitleTask::getExportState, State.QUEUED)
                .orderByAsc(SubtitleTask::getUpdatedAt, SubtitleTask::getId)
                .last("LIMIT 1 FOR UPDATE SKIP LOCKED").one();
        if (task == null) {
            return null;
        }
        if (task.getState() == State.QUEUED) {
            task.setState(State.RUNNING);
        } else {
            task.setExportState(State.RUNNING);
        }
        update(task);
        return task;
    }

    @Override
    public void perform(SubtitleTask task) {
        if (task.getState() == State.RUNNING) {
            String recognized = encode(media.transcribe(task));
            lambdaUpdate().eq(SubtitleTask::getId, task.getId()).eq(SubtitleTask::getState, State.RUNNING)
                    .set(SubtitleTask::getOriginalTimeline, recognized).set(SubtitleTask::getTimeline, recognized)
                    .set(SubtitleTask::getVersion, 1).set(SubtitleTask::getState, State.READY)
                    .set(SubtitleTask::getUpdatedAt, OffsetDateTime.now()).update();
        } else {
            media.renderVideo(task, cues(task));
            lambdaUpdate().eq(SubtitleTask::getId, task.getId()).eq(SubtitleTask::getExportState, State.RUNNING)
                    .set(SubtitleTask::getExportState, State.READY).set(SubtitleTask::getUpdatedAt, OffsetDateTime.now())
                    .update();
        }
    }

    @Override
    public void fail(SubtitleTask task, String message) {
        if (task.getState() == State.RUNNING) {
            lambdaUpdate().eq(SubtitleTask::getId, task.getId()).eq(SubtitleTask::getState, State.RUNNING)
                    .set(SubtitleTask::getState, State.FAILED).set(SubtitleTask::getError, message)
                    .set(SubtitleTask::getUpdatedAt, OffsetDateTime.now()).update();
        } else {
            lambdaUpdate().eq(SubtitleTask::getId, task.getId()).eq(SubtitleTask::getExportState, State.RUNNING)
                    .set(SubtitleTask::getExportState, State.FAILED).set(SubtitleTask::getExportError, message)
                    .set(SubtitleTask::getUpdatedAt, OffsetDateTime.now()).update();
        }
    }

    private Reference reference(long ownerId, SubtitleRequests.Create request) {
        String script = Objects.toString(request.script(), "").strip();
        if (request.draftVersionId() == null && request.draftId() == null) {
            return new Reference(script.isBlank() ? "" : "粘贴的逐字稿", script);
        }
        if (!script.isBlank() || request.draftId() == null || request.draftVersionId() == null) {
            throw WorkspaceException.badRequest("请选择一个已保存的稿件版本。");
        }
        var version = drafts.readVersion(ownerId, request.draftId(), request.draftVersionId());
        return new Reference(version.version().title(), version.content());
    }

    private SubtitleTask owned(long ownerId, long id, boolean lock) {
        LambdaQueryWrapper<SubtitleTask> query = new LambdaQueryWrapper<SubtitleTask>()
                .eq(SubtitleTask::getId, id).eq(SubtitleTask::getOwnerId, ownerId);
        if (lock) query.last("FOR UPDATE");
        SubtitleTask task = getOne(query);
        if (task == null) {
            throw WorkspaceException.notFound("找不到这个字幕任务。");
        }
        return task;
    }

    private static void requireVersion(SubtitleTask task, int version) {
        requireReady(task);
        if (task.getVersion() != version) {
            throw WorkspaceException.conflict("时间轴已有更新，请刷新后再操作。");
        }
    }

    private static void requireReady(SubtitleTask task) {
        if (task.getState() != State.READY) {
            throw WorkspaceException.conflict("字幕还未准备好。");
        }
    }

    private void update(SubtitleTask task) {
        task.setUpdatedAt(OffsetDateTime.now());
        updateById(task);
    }

    private SubtitleViews.Detail detail(SubtitleTask task) {
        return new SubtitleViews.Detail(SubtitleViews.Summary.from(task), task.getDurationMs(), task.getReferenceTitle(),
                task.getVersion(), cues(task), task.getError(), task.getExportError());
    }

    private List<Cue> cues(SubtitleTask task) {
        try {
            return json.readValue(task.getTimeline(), new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read saved subtitle timeline", exception);
        }
    }

    private String encode(List<Cue> cues) {
        try {
            return json.writeValueAsString(cues);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to store subtitle timeline", exception);
        }
    }

    private static String sourceName(String raw) {
        String name = Objects.toString(raw, "").replaceFirst("^.*[\\\\/]", "").replaceAll("\\p{Cntrl}", "").strip();
        return name.isBlank() ? "视频.mp4" : name.substring(0, Math.min(name.length(), 240));
    }

    private static String stem(SubtitleTask task) {
        return task.getSourceName().replaceFirst("(?i)\\.mp4$", "").replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private record Reference(String title, String text) {
    }
}
