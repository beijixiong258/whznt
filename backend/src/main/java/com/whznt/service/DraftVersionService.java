package com.whznt.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.whznt.dto.WritingInput;
import com.whznt.entity.DraftVersion;
import com.whznt.vo.WorkspaceViews;

import java.util.List;

public interface DraftVersionService extends IService<DraftVersion> {

    DraftVersion requireForDraft(long draftId, long versionId);

    List<WorkspaceViews.VersionRow> listForDraft(long draftId);

    DraftVersion append(long draftId, Long baseId, String content, DraftVersion.Source source,
                        WritingInput input, String model);

    WorkspaceViews.Version view(DraftVersion version);

    WritingInput originalInput(DraftVersion version);
}
