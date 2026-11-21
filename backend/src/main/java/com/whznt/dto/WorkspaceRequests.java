package com.whznt.dto;

import com.whznt.entity.GenerationRun;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class WorkspaceRequests {

    private WorkspaceRequests() {
    }

    public record Register(
            @NotBlank(message = "请输入手机号。") String phone,
            @NotNull(message = "请输入密码。") String password) {
    }

    public record Rename(
            @NotBlank(message = "请输入标题。")
            @Size(max = 120, message = "标题最多填写 120 字。") String title) {
    }

    public record SaveDraft(
            @Positive Long baseVersionId,
            @NotBlank(message = "请先写一点内容。")
            @Size(max = 30000, message = "稿件最多保存 30000 字。") String content) {
    }

    public record Generate(
            @NotNull UUID requestId,
            @NotNull GenerationRun.Kind kind,
            @Positive Long conversationId,
            @Positive Long baseVersionId,
            @Valid WritingInput writingInput,
            @Size(max = 12000, message = "单条消息最多填写 12000 字。") String message) {
    }

    public record Rewrite(
            @Positive long draftId,
            @Positive long baseVersionId,
            @NotBlank(message = "请选择正文的一段内容。")
            @Size(max = 6000, message = "单次改写最多选择 6000 字。") String selection,
            @Size(max = 1500) String before,
            @Size(max = 1500) String after,
            @NotNull RewriteAction action,
            @Size(max = 120) String tone) {
    }

    public enum RewriteAction {
        POLISH, SHORTEN, TONE
    }
}
