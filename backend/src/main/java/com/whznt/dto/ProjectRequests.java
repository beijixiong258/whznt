package com.whznt.dto;

import com.whznt.entity.ProjectMaterial;
import com.whznt.entity.ProjectStep;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class ProjectRequests {

    private ProjectRequests() {
    }

    public record Profile(
            @NotBlank(message = "请输入人物名称。") @Size(max = 120) String name,
            @Size(max = 6000, message = "人物简介最多 6000 字。") String summary) {
    }

    public record TextMaterial(
            @Positive Long materialId,
            @Positive Long baseVersionId,
            @NotBlank(message = "请输入资料名称。") @Size(max = 120) String name,
            @NotNull ProjectMaterial.Kind kind,
            @NotBlank(message = "请填写资料原文。") @Size(max = 200000) String text) {
    }

    public record ExistingArtifact(
            @NotNull ProjectStep step,
            @NotBlank(message = "请粘贴已有成果。") @Size(max = 30000) String content) {
    }

    public record Generate(
            @NotNull UUID requestId,
            @NotNull ProjectStep step,
            @Positive Long artifactId,
            @Positive Long baseVersionId,
            @Positive Long topicVersionId,
            @Positive Integer topicIndex,
            @Min(15) @Max(600) Integer durationSeconds,
            @Size(max = 1500) String instructions) {
    }
}
