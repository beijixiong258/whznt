package com.whznt.controller;

import com.whznt.dto.WorkspaceRequests;
import com.whznt.entity.Conversation;
import com.whznt.service.ChatMessageService;
import com.whznt.service.ConversationService;
import com.whznt.service.DraftService;
import com.whznt.config.AccountPrincipal;
import com.whznt.vo.WorkspaceViews;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class HistoryController {

    private final ConversationService conversations;
    private final ChatMessageService messages;
    private final DraftService drafts;

    @GetMapping
    public WorkspaceViews.History history(@AuthenticationPrincipal AccountPrincipal principal,
                                          @RequestParam(defaultValue = "") @Size(max = 120) String q,
                                          @RequestParam(defaultValue = "1") @Min(1) int page) {
        return conversations.history(principal.accountId(), q, page);
    }

    @GetMapping("/{id}")
    public WorkspaceViews.ConversationDetail detail(@AuthenticationPrincipal AccountPrincipal principal,
                                                     @PathVariable long id) {
        Conversation conversation = conversations.requireOwned(principal.accountId(), id);
        if (conversation.getKind() == Conversation.Kind.WRITING) {
            return new WorkspaceViews.ConversationDetail(WorkspaceViews.ConversationRow.from(conversation),
                    List.of(), drafts.viewForConversation(id));
        }
        List<WorkspaceViews.Message> rows = messages.forConversation(id).stream().map(WorkspaceViews.Message::from)
                .toList();
        return new WorkspaceViews.ConversationDetail(WorkspaceViews.ConversationRow.from(conversation), rows, null);
    }

    @PatchMapping("/{id}")
    public WorkspaceViews.ConversationRow rename(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
                                                  @Valid @RequestBody WorkspaceRequests.Rename request) {
        return conversations.rename(principal.accountId(), id, request.title());
    }
}
