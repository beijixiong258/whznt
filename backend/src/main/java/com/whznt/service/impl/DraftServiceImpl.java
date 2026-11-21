package com.whznt.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.whznt.dto.WorkspaceRequests;
import com.whznt.dto.WritingInput;
import com.whznt.entity.Conversation;
import com.whznt.entity.Draft;
import com.whznt.entity.DraftVersion;
import com.whznt.exception.WorkspaceException;
import com.whznt.mapper.DraftMapper;
import com.whznt.mapper.ProjectVersionContextMapper;
import com.whznt.service.ConversationService;
import com.whznt.service.DraftService;
import com.whznt.service.DraftVersionService;
import com.whznt.vo.WorkspaceViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DraftServiceImpl extends ServiceImpl<DraftMapper, Draft> implements DraftService {

    private final ConversationService conversations;
    private final DraftVersionService versions;
    private final ProjectVersionContextMapper projectContexts;

    @Override
    public Draft requireOwned(long ownerId, long draftId) {
        Draft draft = getById(draftId);
        if (draft == null) {
            throw WorkspaceException.notFound("找不到这篇稿件。");
        }
        conversations.requireOwned(ownerId, draft.getConversationId());
        return draft;
    }

    private Draft forConversation(long conversationId) {
        return lambdaQuery().eq(Draft::getConversationId, conversationId).one();
    }

    @Override
    public DraftVersion currentVersion(long conversationId) {
        Draft draft = forConversation(conversationId);
        return draft == null || draft.getCurrentVersionId() == null
                ? null : versions.requireForDraft(draft.getId(), draft.getCurrentVersionId());
    }

    @Override
    public DraftVersion requireCurrent(long conversationId, Long expectedVersion) {
        Draft draft = forConversation(conversationId);
        Long current = draft == null ? null : draft.getCurrentVersionId();
        if (!Objects.equals(current, expectedVersion)) {
            throw WorkspaceException.conflict("这篇稿件有更新的版本。请从历史打开最新版后再操作。");
        }
        return current == null ? null : versions.requireForDraft(draft.getId(), current);
    }

    @Override
    public WorkspaceViews.Manuscript viewForConversation(long conversationId) {
        Draft draft = forConversation(conversationId);
        if (draft == null) {
            return null;
        }
        return new WorkspaceViews.Manuscript(draft.getId(), conversationId,
                versions.view(versions.requireForDraft(draft.getId(), draft.getCurrentVersionId())),
                versions.listForDraft(draft.getId()));
    }

    @Override
    @Transactional
    public WorkspaceViews.Manuscript saveManual(long ownerId, Long conversationId,
                                               WorkspaceRequests.SaveDraft request) {
        Conversation conversation = conversationId == null
                ? conversations.create(ownerId, Conversation.Kind.WRITING, null)
                : conversations.lockOwned(ownerId, conversationId);
        if (conversation.getKind() != Conversation.Kind.WRITING) {
            throw WorkspaceException.badRequest("请在写稿工作台保存稿件。");
        }

        DraftVersion base = requireCurrent(conversation.getId(), request.baseVersionId());
        WritingInput input = base == null ? null : versions.originalInput(base);
        String model = base == null ? null : base.getModel();
        DraftVersion saved = saveVersion(conversation, request.baseVersionId(), request.content(),
                DraftVersion.Source.EDITED, input, model);
        if (base != null && conversation.getProjectId() != null) {
            projectContexts.copy(base.getId(), saved.getId());
        }
        return viewForConversation(conversation.getId());
    }

    @Override
    public DraftVersion saveGenerated(Conversation conversation, Long baseVersionId, String content,
                                      WritingInput input, String model) {
        requireCurrent(conversation.getId(), baseVersionId);
        return saveVersion(conversation, baseVersionId, content, DraftVersion.Source.GENERATED, input, model);
    }

    @Override
    public DraftVersion saveProjectVersion(Conversation conversation, Long baseVersionId, String content,
                                           DraftVersion.Source source, String model) {
        requireCurrent(conversation.getId(), baseVersionId);
        return saveVersion(conversation, baseVersionId, content, source, null, model);
    }

    @Override
    public WorkspaceViews.Version readVersion(long ownerId, long draftId, long versionId) {
        Draft draft = requireOwned(ownerId, draftId);
        return versions.view(versions.requireForDraft(draft.getId(), versionId));
    }

    private DraftVersion saveVersion(Conversation conversation, Long baseVersionId, String content,
                                     DraftVersion.Source source, WritingInput input, String model) {
        Draft draft = forConversation(conversation.getId());
        if (draft == null) {
            draft = new Draft();
            draft.setConversationId(conversation.getId());
            save(draft);
        }

        DraftVersion version = versions.append(draft.getId(), baseVersionId, content, source, input, model);
        draft.setCurrentVersionId(version.getId());
        updateById(draft);
        conversations.touch(conversation, version.getTitle());
        return version;
    }
}
