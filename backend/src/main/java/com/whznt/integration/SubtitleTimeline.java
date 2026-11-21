package com.whznt.integration;

import com.whznt.dto.SubtitleRequests.Cue;
import com.whznt.exception.WorkspaceException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class SubtitleTimeline {

    private static final long MAX_SRT_BYTES = 1024 * 1024;
    private static final String STAMP = "(\\d{2,6}):([0-5]\\d):([0-5]\\d)[,.](\\d{3})";
    private static final Pattern TIMING = Pattern.compile(STAMP + "\\h*-->\\h*" + STAMP + "(?:\\h+.*)?");
    private static final Pattern SRT_STYLE = Pattern.compile("(?i)</?(?:i|b|u|font)(?:\\s[^<>]*)?>");

    public List<Cue> read(MultipartFile file, long durationMs) {
        if (file.isEmpty() || file.getSize() > MAX_SRT_BYTES) {
            throw WorkspaceException.badRequest("请选择不超过 1 MB 的 SRT 文件。");
        }
        String content;
        try {
            content = decode(file.getBytes());
        } catch (IOException exception) {
            throw WorkspaceException.badRequest("无法读取 SRT，请保存为 UTF-8 后重试。");
        }
        List<Cue> cues = new ArrayList<>();
        String normalized = content.replaceFirst("^\\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').strip();
        for (String block : normalized.split("\n\\h*\n+")) {
            String[] parts = block.strip().split("\n", 3);
            boolean numbered = parts[0].strip().matches("\\d+");
            if (parts.length < (numbered ? 3 : 2)) {
                throw invalidCue(cues.size() + 1, "缺少时间或内容");
            }
            String timing = parts[numbered ? 1 : 0].strip();
            var match = TIMING.matcher(timing);
            if (!match.matches()) {
                throw invalidCue(cues.size() + 1, "时间格式不正确");
            }
            String text = numbered ? parts[2] : String.join("\n", block.strip().lines().skip(1).toList());
            cues.add(new Cue(milliseconds(match.group(1), match.group(2), match.group(3), match.group(4)),
                    milliseconds(match.group(5), match.group(6), match.group(7), match.group(8)),
                    SRT_STYLE.matcher(text).replaceAll("")));
        }
        return validate(cues, durationMs);
    }

    public List<Cue> validate(List<Cue> cues, long durationMs) {
        if (cues.isEmpty() || cues.size() > 5000) {
            throw WorkspaceException.badRequest("字幕需要包含 1 至 5,000 条。");
        }
        List<Cue> normalized = new ArrayList<>(cues.size());
        long previousEnd = 0;
        for (Cue cue : cues) {
            int row = normalized.size() + 1;
            if (cue.startMs() < previousEnd || cue.endMs() <= cue.startMs() || cue.endMs() > durationMs) {
                throw invalidCue(row, "时间超出片长、结束早于开始或与上一条重叠");
            }
            String text = cue.text().lines().map(String::strip).filter(line -> !line.isBlank())
                    .collect(Collectors.joining("\n"));
            if (text.isBlank() || text.length() > 300 || text.indexOf('\0') >= 0) {
                throw invalidCue(row, "请输入 1 至 300 字正文");
            }
            normalized.add(new Cue(cue.startMs(), cue.endMs(), text));
            previousEnd = cue.endMs();
        }
        return normalized;
    }

    public String render(List<Cue> cues) {
        StringBuilder srt = new StringBuilder();
        int row = 0;
        for (Cue cue : cues) {
            srt.append(++row).append('\n').append(timestamp(cue.startMs())).append(" --> ")
                    .append(timestamp(cue.endMs())).append('\n').append(cue.text()).append("\n\n");
        }
        return srt.toString();
    }

    private static String decode(byte[] data) throws CharacterCodingException {
        if (data.length >= 2 && ((data[0] == (byte) 0xff && data[1] == (byte) 0xfe)
                || (data[0] == (byte) 0xfe && data[1] == (byte) 0xff))) {
            return StandardCharsets.UTF_16.newDecoder().decode(ByteBuffer.wrap(data)).toString();
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException exception) {
            return Charset.forName("GB18030").newDecoder().decode(ByteBuffer.wrap(data)).toString();
        }
    }

    private static long milliseconds(String hh, String mm, String ss, String millis) {
        return ((Long.parseLong(hh) * 60 + Long.parseLong(mm)) * 60 + Long.parseLong(ss)) * 1000
                + Long.parseLong(millis);
    }

    private static String timestamp(long millis) {
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d",
                millis / 3_600_000, millis / 60_000 % 60, millis / 1000 % 60, millis % 1000);
    }

    private static WorkspaceException invalidCue(int row, String reason) {
        return WorkspaceException.badRequest("第 " + row + " 条字幕" + reason + "。");
    }
}
