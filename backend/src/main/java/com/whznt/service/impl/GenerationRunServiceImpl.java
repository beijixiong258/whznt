package com.whznt.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whznt.dto.WorkspaceRequests;
import com.whznt.dto.WritingInput;
import com.whznt.entity.ChatMessage;
import com.whznt.entity.Conversation;
import com.whznt.entity.DraftVersion;
import com.whznt.entity.GenerationRun;
import com.whznt.exception.WorkspaceException;
import com.whznt.mapper.GenerationRunMapper;
import com.whznt.service.ChatMessageService;
import com.whznt.service.ConversationService;
import com.whznt.service.DraftService;
import com.whznt.service.DraftVersionService;
import com.whznt.service.GenerationRunService;
import com.whznt.vo.WorkspaceViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GenerationRunServiceImpl extends ServiceImpl<GenerationRunMapper, GenerationRun>
        implements GenerationRunService {

    private final ObjectMapper json;
    private final ConversationService conversations;
    private final ChatMessageService messages;
    private final DraftService drafts;
    private final DraftVersionService versions;

    @Override
    @Transactional
    public Prepared prepare(long ownerId, WorkspaceRequests.Generate request) {
        GenerationRun reservation = new GenerationRun();
        reservation.setOwnerId(ownerId);
        reservation.setRequestKey(request.requestId().toString());
        reservation.setKind(request.kind());
        reservation.setState(GenerationRun.State.RUNNING);
        reservation.setInputSnapshot(snapshot(request));
        boolean fresh = baseMapper.reserve(reservation) == 1;
        GenerationRun run = lambdaQuery().eq(GenerationRun::getOwnerId, ownerId)
                .eq(GenerationRun::getRequestKey, reservation.getRequestKey()).one();
        if (!fresh) {
            return replay(run, reservation.getInputSnapshot());
        }

        boolean chat = request.kind() == GenerationRun.Kind.CHAT;
        boolean regenerate = request.kind() == GenerationRun.Kind.REGENERATE;
        Conversation.Kind kind = chat ? Conversation.Kind.CHAT : Conversation.Kind.WRITING;
        String message = chat ? requireMessage(request.message()) : null;
        WritingInput input = chat ? null : WritingInput.normalized(request.writingInput());
        if (regenerate && (request.conversationId() == null || request.baseVersionId() == null)) {
            throw WorkspaceException.badRequest("请打开要重新生成的稿件版本。");
        }
        if (request.conversationId() == null && request.baseVersionId() != null) {
            throw WorkspaceException.badRequest("当前请求的稿件版本不属于新会话。");
        }

        Conversation conversation = request.conversationId() == null
                ? conversations.create(ownerId, kind, chat ? message : input.topic())
                : conversations.lockOwned(ownerId, request.conversationId());
        if (conversation.getKind() != kind) {
            throw WorkspaceException.badRequest("请在对应的写稿或对话入口操作。");
        }
        if (conversation.getProjectId() != null) {
            throw WorkspaceException.badRequest("请从人物项目重新生成这个成果。");
        }

        long conversationId = conversation.getId();
        baseMapper.expireAbandoned(conversationId);
        if (lambdaQuery().eq(GenerationRun::getConversationId, conversationId)
                .eq(GenerationRun::getState, GenerationRun.State.RUNNING).exists()) {
            throw WorkspaceException.conflict("这条会话正在生成，请稍后重试。");
        }

        List<ChatMessage> history = List.of();
        if (chat) {
            history = messages.recent(conversationId);
        } else {
            DraftVersion base = drafts.requireCurrent(conversationId, request.baseVersionId());
            if (regenerate) {
                input = versions.originalInput(base);
                if (input == null) {
                    throw WorkspaceException.badRequest("这篇稿件没有可复用的生成参数，请按题写一版。");
                }
            }
        }

        run.setConversationId(conversationId);
        updateById(run);
        return new Prepared(run, false, request.baseVersionId(), input, message, history);
    }

    @Override
    @Transactional
    public WorkspaceViews.GenerationEvent complete(Prepared work, String content, String model) {
        GenerationRun prepared = work.run();
        Conversation conversation = conversations.lockOwned(prepared.getOwnerId(), prepared.getConversationId());
        GenerationRun run = baseMapper.lockById(prepared.getId());
        if (run.getState() != GenerationRun.State.RUNNING) {
            throw WorkspaceException.conflict("这次生成已结束，请重新发起。");
        }

        if (run.getKind() == GenerationRun.Kind.CHAT) {
            messages.appendExchange(conversation.getId(), work.message(), content);
            conversations.touch(conversation, null);
        } else {
            DraftVersion version = drafts.saveGenerated(conversation, work.baseVersionId(), content,
                    work.writingInput(), model);
            run.setVersionId(version.getId());
        }
        run.setState(GenerationRun.State.COMPLETED);
        updateById(run);
        return WorkspaceViews.GenerationEvent.done(conversation.getId(), run.getVersionId());
    }

    @Override
    public void fail(long runId) {
        lambdaUpdate().eq(GenerationRun::getId, runId)
                .eq(GenerationRun::getState, GenerationRun.State.RUNNING)
                .set(GenerationRun::getState, GenerationRun.State.FAILED)
                .update();
    }

    private Prepared replay(GenerationRun run, String input) {
        if (!run.getInputSnapshot().equals(input)) {
            throw WorkspaceException.conflict("请求编号已被使用，请重新发起。");
        }
        return switch (run.getState()) {
            case COMPLETED -> new Prepared(run, true, null, null, null, List.of());
            case RUNNING -> throw WorkspaceException.conflict("这次生成仍在进行，请稍后再查看。");
            case FAILED -> throw WorkspaceException.conflict("这次生成已失败，请重新发起。");
        };
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw WorkspaceException.badRequest("请输入想讨论的内容。");
        }
        return message.strip();
    }

    private String snapshot(WorkspaceRequests.Generate request) {
        try {
            return json.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to store the generation request", exception);
        }
    }
}
