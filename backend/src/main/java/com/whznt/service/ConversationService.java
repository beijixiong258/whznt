package com.whznt.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.whznt.entity.Conversation;
import com.whznt.vo.WorkspaceViews;

import java.time.OffsetDateTime;

public interface ConversationService extends IService<Conversation> {

    Conversation requireOwned(long ownerId, long id);

    Conversation lockOwned(long ownerId, long id);

    Conversation create(long ownerId, Conversation.Kind kind, String title);

    Conversation createForProject(long ownerId, long projectId, String title);

    WorkspaceViews.History history(long ownerId, String query, String assistant, Conversation.Kind kind,
                                   OffsetDateTime since, int page);

    WorkspaceViews.ConversationRow rename(long ownerId, long id, String title);

    void deleteConversation(long ownerId, long id);

    void touch(Conversation conversation, String titleIfUnnamed);
}
