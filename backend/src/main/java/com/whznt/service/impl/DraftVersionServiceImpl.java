package com.whznt.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whznt.dto.WritingInput;
import com.whznt.entity.DraftVersion;
import com.whznt.exception.WorkspaceException;
import com.whznt.mapper.DraftVersionMapper;
import com.whznt.service.DraftVersionService;
import com.whznt.vo.WorkspaceViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DraftVersionServiceImpl extends ServiceImpl<DraftVersionMapper, DraftVersion>
        implements DraftVersionService {

    private final ObjectMapper json;

    @Override
    public DraftVersion requireForDraft(long draftId, long versionId) {
        DraftVersion row = lambdaQuery().eq(DraftVersion::getDraftId, draftId)
                .eq(DraftVersion::getId, versionId).one();
        if (row == null) {
            throw WorkspaceException.notFound("找不到这个稿件版本。");
        }
        return row;
    }

    @Override
    public List<WorkspaceViews.VersionRow> listForDraft(long draftId) {
        return lambdaQuery()
                .select(DraftVersion::getId, DraftVersion::getTitle, DraftVersion::getSource,
                        DraftVersion::getCreatedAt, DraftVersion::getBaseVersionId)
                .eq(DraftVersion::getDraftId, draftId)
                .orderByDesc(DraftVersion::getId)
                .list().stream().map(WorkspaceViews.VersionRow::from).toList();
    }

    @Override
    public DraftVersion append(long draftId, Long baseId, String content, DraftVersion.Source source,
                               WritingInput input, String model) {
        DraftVersion row = new DraftVersion();
        row.setDraftId(draftId);
        row.setBaseVersionId(baseId);
        row.setTitle(titleOf(content));
        row.setContent(content);
        row.setSource(source);
        row.setWritingInput(encode(input));
        row.setModel(model);
        row.setCreatedAt(OffsetDateTime.now());
        save(row);
        return row;
    }

    @Override
    public WorkspaceViews.Version view(DraftVersion version) {
        return new WorkspaceViews.Version(WorkspaceViews.VersionRow.from(version), version.getContent(),
                originalInput(version), version.getModel());
    }

    @Override
    public WritingInput originalInput(DraftVersion version) {
        if (version.getWritingInput() == null) {
            return null;
        }
        try {
            return json.readValue(version.getWritingInput(), WritingInput.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read the stored writing input", exception);
        }
    }

    private String encode(WritingInput input) {
        if (input == null) {
            return null;
        }
        try {
            return json.writeValueAsString(input);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to store the writing input", exception);
        }
    }

    private static String titleOf(String content) {
        return content.lines().map(String::strip)
                .filter(line -> !line.isBlank())
                .findFirst()
                .map(line -> line.replaceFirst("^#{1,6}\\s*", "").strip())
                .filter(line -> !line.isBlank())
                .map(ConversationServiceImpl::shortTitle)
                .orElse("未命名稿件");
    }
}
