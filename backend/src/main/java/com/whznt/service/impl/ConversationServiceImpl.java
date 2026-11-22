package com.whznt.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.whznt.entity.Conversation;
import com.whznt.exception.WorkspaceException;
import com.whznt.mapper.ConversationMapper;
import com.whznt.service.ConversationService;
import com.whznt.vo.WorkspaceViews;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation>
        implements ConversationService {

    private static final int PAGE_SIZE = 20;
    private static final int MAX_TITLE_LENGTH = 120;
    private static final String UNNAMED = "未命名稿件";

    @Override
    public Conversation requireOwned(long ownerId, long id) {
        return requireRow(baseMapper.findOwned(id, ownerId));
    }

    @Override
    public Conversation lockOwned(long ownerId, long id) {
        return requireRow(baseMapper.lockOwned(id, ownerId));
    }

    @Override
    public Conversation create(long ownerId, Conversation.Kind kind, String title) {
        return create(ownerId, kind, title, null);
    }

    @Override
    public Conversation createForProject(long ownerId, long projectId, String title) {
        return create(ownerId, Conversation.Kind.WRITING, title, projectId);
    }

    private Conversation create(long ownerId, Conversation.Kind kind, String title, Long projectId) {
        Conversation row = new Conversation();
        row.setOwnerId(ownerId);
        row.setProjectId(projectId);
        row.setKind(kind);
        row.setTitle(title == null || title.isBlank() ? UNNAMED : shortTitle(title));
        row.setUpdatedAt(OffsetDateTime.now());
        save(row);
        return row;
    }

    @Override
    public WorkspaceViews.History history(long ownerId, String query, String assistant, Conversation.Kind kind,
                                          OffsetDateTime since, int page) {
        List<WorkspaceViews.HistoryItem> rows = baseMapper.findHistory(ownerId, query.strip(), assistant.strip(),
                kind == null ? "" : kind.name(), since, PAGE_SIZE + 1, (long) (page - 1) * PAGE_SIZE);
        return new WorkspaceViews.History(rows.subList(0, Math.min(rows.size(), PAGE_SIZE)), rows.size() > PAGE_SIZE);
    }

    @Override
    @Transactional
    public WorkspaceViews.ConversationRow rename(long ownerId, long id, String title) {
        Conversation row = lockOwned(ownerId, id);
        row.setTitle(shortTitle(title));
        row.setUpdatedAt(OffsetDateTime.now());
        updateById(row);
        return WorkspaceViews.ConversationRow.from(row);
    }

    @Override
    @Transactional
    public void deleteConversation(long ownerId, long id) {
        removeById(lockOwned(ownerId, id).getId());
    }

    @Override
    public void touch(Conversation row, String titleIfUnnamed) {
        if (UNNAMED.equals(row.getTitle()) && titleIfUnnamed != null && !titleIfUnnamed.isBlank()) {
            row.setTitle(shortTitle(titleIfUnnamed));
        }
        row.setUpdatedAt(OffsetDateTime.now());
        updateById(row);
    }

    public static String shortTitle(String text) {
        String line = text.strip().lines().findFirst().orElse(UNNAMED);
        int characters = line.codePointCount(0, line.length());
        return characters <= MAX_TITLE_LENGTH ? line : line.substring(0, line.offsetByCodePoints(0, MAX_TITLE_LENGTH));
    }

    private static Conversation requireRow(Conversation row) {
        if (row == null) {
            throw WorkspaceException.notFound("找不到这条会话。");
        }
        return row;
    }
}
