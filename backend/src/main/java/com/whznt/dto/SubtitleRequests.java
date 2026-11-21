package com.whznt.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public final class SubtitleRequests {

    private SubtitleRequests() {
    }

    public record Create(
            @NotNull MultipartFile video, MultipartFile srt,
            @Positive Long draftId, @Positive Long draftVersionId,
            @Size(max = 30_000) String script) {
    }

    public record Cue(@PositiveOrZero long startMs, @Positive long endMs,
                      @NotBlank @Size(max = 300) String text) {
    }

    public record Edit(@Positive int version,
                       @NotEmpty @Size(max = 5000) List<@NotNull @Valid Cue> cues) {
    }

    public record Export(@Positive int version) {
    }
}
