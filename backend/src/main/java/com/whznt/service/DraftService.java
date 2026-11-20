package com.whznt.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.whznt.dto.WorkspaceRequests;
import com.whznt.dto.WritingInput;
import com.whznt.entity.Conversation;
import com.whznt.entity.Draft;
import com.whznt.entity.DraftVersion;
import com.whznt.vo.WorkspaceViews;

public interface DraftService extends IService<Draft> {

    Draft requireOwned(long ownerId, long draftId);

    DraftVersion requireCurrent(long conversationId, Long expectedVersion);

    WorkspaceViews.Manuscript viewForConversation(long conversationId);

    WorkspaceViews.Manuscript saveManual(long ownerId, Long conversationId, WorkspaceRequests.SaveDraft request);

    DraftVersion saveGenerated(Conversation conversation, Long baseVersionId, String content,
                               WritingInput input, String model);

    WorkspaceViews.Version readVersion(long ownerId, long draftId, long versionId);
}
