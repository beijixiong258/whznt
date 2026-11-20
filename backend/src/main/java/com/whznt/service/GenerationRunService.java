package com.whznt.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.whznt.dto.WorkspaceRequests;
import com.whznt.dto.WritingInput;
import com.whznt.entity.ChatMessage;
import com.whznt.entity.GenerationRun;
import com.whznt.vo.WorkspaceViews;

import java.util.List;

public interface GenerationRunService extends IService<GenerationRun> {

    Prepared prepare(long ownerId, WorkspaceRequests.Generate request);

    WorkspaceViews.GenerationEvent complete(Prepared work, String content, String model);

    void fail(long runId);

    record Prepared(
            GenerationRun run, boolean replay, Long baseVersionId, WritingInput writingInput,
            String message, List<ChatMessage> history) {
    }
}
