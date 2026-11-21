package com.whznt.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whznt.dto.SubtitleRequests.Cue;
import com.whznt.entity.SubtitleTask;
import com.whznt.exception.WorkspaceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class SubtitleMedia {

    private final PrivateFiles files;
    private final SubtitleTimeline timelines;
    private final ObjectMapper json;
    private final String ffmpeg;
    private final String ffprobe;
    private final String python;
    private final Path transcriber;
    private final String model;

    public SubtitleMedia(PrivateFiles files, SubtitleTimeline timelines, ObjectMapper json,
                         @Value("${whznt.subtitle.ffmpeg:ffmpeg}") String ffmpeg,
                         @Value("${whznt.subtitle.ffprobe:ffprobe}") String ffprobe,
                         @Value("${whznt.subtitle.python:python}") String python,
                         @Value("${whznt.subtitle.script:subtitle/transcribe.py}") String script,
                         @Value("${whznt.subtitle.model:small}") String model) {
        this.files = files;
        this.timelines = timelines;
        this.json = json;
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.python = python;
        this.transcriber = Path.of(script).toAbsolutePath().normalize();
        this.model = model;
    }

    public Video inspect(Path video) {
        Path output = null;
        try {
            output = Files.createTempFile("whznt-probe-", ".json");
            run(video.getParent(), output, Duration.ofSeconds(20), List.of(ffprobe, "-v", "error",
                    "-protocol_whitelist", "file", "-show_entries", "format=duration,format_name:stream=codec_type,codec_name",
                    "-of", "json", video.toString()));
            JsonNode metadata = json.readTree(output.toFile());
            JsonNode format = metadata.path("format");
            boolean browserVideo = false;
            boolean audio = false;
            boolean incompatibleAudio = false;
            for (JsonNode stream : metadata.path("streams")) {
                switch (stream.path("codec_type").asText()) {
                    case "video" -> browserVideo |= stream.path("codec_name").asText().equals("h264");
                    case "audio" -> {
                        audio = true;
                        incompatibleAudio |= !stream.path("codec_name").asText().equals("aac");
                    }
                    default -> { }
                }
            }
            long duration = Math.round(format.path("duration").asDouble() * 1000);
            if (!browserVideo || incompatibleAudio || !format.path("format_name").asText().contains("mp4") || duration <= 0) {
                throw WorkspaceException.badRequest("请选择 H.264 编码的 MP4 视频，声音使用 AAC 编码。");
            }
            return new Video(duration, audio);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw WorkspaceException.badRequest("无法读取视频，请检查 MP4 文件及 FFprobe 配置。");
        } finally {
            removeTemporary(output);
        }
    }

    public List<Cue> transcribe(SubtitleTask task) {
        Path audio = null;
        try {
            Path directory = directory(task.getId());
            Path script = directory.resolve("reference.txt");
            Path output = directory.resolve("recognition.json");
            audio = directory.resolve("recognition.wav");
            Files.writeString(script, task.getReferenceText());
            run(directory, null, Duration.ofMinutes(5), List.of(ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error",
                    "-y", "-protocol_whitelist", "file", "-copyts", "-start_at_zero", "-i",
                    files.requireOriginal(task.getSourceKey()).toString(), "-map", "0:a:0",
                    "-af", "aresample=async=1:first_pts=0", "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le",
                    audio.toString()));
            run(directory, null, Duration.ofMinutes(25), List.of(python, transcriber.toString(),
                    audio.toString(), script.toString(), output.toString(), model, task.getDurationMs().toString()));
            List<Cue> cues = json.readValue(output.toFile(), new TypeReference<>() { });
            if (cues.isEmpty()) {
                throw WorkspaceException.badRequest("没有识别到清晰语音，可以检查录音或直接导入 SRT。");
            }
            return timelines.validate(cues, task.getDurationMs());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Subtitle recognition command failed for task {} ({})", task.getId(), exception.getClass().getSimpleName());
            throw WorkspaceException.unavailable("识别没有完成，请检查语音模型和视频后重试。");
        } finally {
            removeTemporary(audio);
        }
    }

    public void renderVideo(SubtitleTask task, List<Cue> cues) {
        try {
            Path directory = directory(task.getId());
            Files.writeString(directory.resolve("timeline.srt"), timelines.render(cues));
            Path pending = directory.resolve("export-pending.mp4");
            run(directory, null, Duration.ofMinutes(40), List.of(ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error",
                    "-y", "-protocol_whitelist", "file", "-i", files.requireOriginal(task.getSourceKey()).toString(),
                    "-map", "0:v:0", "-map", "0:a:0?", "-vf",
                    "subtitles=timeline.srt:charenc=UTF-8:force_style='FontName=Noto Sans CJK SC,Outline=2,MarginV=22'",
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p",
                    "-c:a", "copy", "-movflags", "+faststart", pending.toString()));
            Files.move(pending, directory.resolve(exportName(task.getVersion())), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Subtitle rendering failed for task {} ({})", task.getId(), exception.getClass().getSimpleName());
            throw WorkspaceException.unavailable("视频导出没有完成，请检查存储空间或 FFmpeg 配置后重试。");
        }
    }

    public Resource exported(long taskId, int version) {
        Path path = files.subtitleDirectory(taskId).resolve(exportName(version));
        if (!Files.isRegularFile(path)) {
            throw WorkspaceException.notFound("导出文件已不可用。");
        }
        return new FileSystemResource(path);
    }

    private static String exportName(int version) {
        return "export-" + version + ".mp4";
    }

    private Path directory(long taskId) throws IOException {
        return Files.createDirectories(files.subtitleDirectory(taskId));
    }

    private static void run(Path directory, Path output, Duration timeout, List<String> command)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile())
                .redirectOutput(output == null ? ProcessBuilder.Redirect.DISCARD : ProcessBuilder.Redirect.to(output.toFile()))
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().remove("SPRING_AI_OPENAI_API_KEY");
        Process process = builder.start();
        try {
            process.getOutputStream().close();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS) || process.exitValue() != 0) {
                throw new IOException("Media process did not complete");
            }
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static void removeTemporary(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("Could not remove a temporary media file");
        }
    }

    public record Video(long durationMs, boolean audio) {
    }
}
