package com.whznt.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record WritingInput(
        @Size(max = 300, message = "主题最多填写 300 字。") String topic,
        @Size(max = 500, message = "切入角度最多填写 500 字。") String angle,
        @Size(max = 120, message = "受众最多填写 120 字。") String audience,
        @Size(max = 120, message = "语气最多填写 120 字。") String tone,
        @Min(value = 15, message = "口播时长至少 15 秒。")
        @Max(value = 600, message = "口播时长最多 10 分钟。") Integer durationSeconds,
        Format format,
        @Size(max = 12000, message = "参考资料最多填写 12000 字。") String background) {

    public enum Format {
        TALK, ARTICLE
    }

    public static WritingInput normalized(WritingInput input) {
        if (input == null) {
            return new WritingInput("", "", "", "", 150, Format.TALK, "");
        }

        Format format = input.format == null ? Format.TALK : input.format;
        Integer duration = format == Format.ARTICLE ? null : input.durationSeconds;
        if (format == Format.TALK && duration == null) {
            duration = 150;
        }

        return new WritingInput(clean(input.topic), clean(input.angle), clean(input.audience),
                clean(input.tone), duration, format, clean(input.background));
    }

    private static String clean(String value) {
        return value == null || value.chars().allMatch(WritingInput::isBlankCharacter) ? "" : value.strip();
    }

    private static boolean isBlankCharacter(int character) {
        // Pasted text can contain invisible characters outside Java's whitespace categories.
        return switch (character) {
            case 0, 0x180E, 0x200C, 0x202A, 0x2800, 0x3164, 0xFEFF -> true;
            default -> Character.isWhitespace(character) || Character.isSpaceChar(character);
        };
    }
}
