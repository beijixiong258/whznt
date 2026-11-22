package com.whznt.vo;

import com.whznt.dto.WritingInput;
import com.whznt.entity.Account.Role;
import com.whznt.entity.ChatMessage;
import com.whznt.entity.Conversation;
import com.whznt.entity.DraftVersion;

import java.time.OffsetDateTime;
import java.util.List;

public final class WorkspaceViews {

    private WorkspaceViews() {
    }

    public record Account(long id, String phone, Role role) {
    }

    public record History(List<ConversationRow> items, boolean hasMore) {
    }

    public record ConversationRow(long id, Conversation.Kind kind, String title, OffsetDateTime updatedAt, Long projectId) {
        public static ConversationRow from(Conversation row) {
            return new ConversationRow(row.getId(), row.getKind(), row.getTitle(), row.getUpdatedAt(), row.getProjectId());
        }
    }

    public record Message(long id, ChatMessage.Role role, String content, OffsetDateTime createdAt) {
        public static Message from(ChatMessage row) {
            return new Message(row.getId(), row.getRole(), row.getContent(), row.getCreatedAt());
        }
    }

    public record ConversationDetail(ConversationRow conversation, List<Message> messages, Manuscript draft) {
    }

    public record Manuscript(long id, long conversationId, Version currentVersion, List<VersionRow> versions) {
    }

    public record VersionRow(
            long id, String title, DraftVersion.Source source, OffsetDateTime createdAt, Long baseVersionId) {
        public static VersionRow from(DraftVersion row) {
            return new VersionRow(row.getId(), row.getTitle(), row.getSource(), row.getCreatedAt(),
                    row.getBaseVersionId());
        }
    }

    public record Version(VersionRow version, String content, WritingInput writingInput, String model) {
    }

    public record RewriteResult(String content) {
    }

    public record GenerationEvent(Long conversationId, Long versionId, String text, String message) {

        public static GenerationEvent started(long id) {
            return new GenerationEvent(id, null, null, null);
        }

        public static GenerationEvent delta(String text) {
            return new GenerationEvent(null, null, text, null);
        }

        public static GenerationEvent done(long id, Long versionId) {
            return new GenerationEvent(id, versionId, null, null);
        }

        public static GenerationEvent failed(long id, String message) {
            return new GenerationEvent(id, null, null, message);
        }
    }
}
