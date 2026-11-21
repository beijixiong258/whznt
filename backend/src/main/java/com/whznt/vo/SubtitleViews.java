package com.whznt.vo;

import com.whznt.dto.SubtitleRequests.Cue;
import com.whznt.entity.SubtitleTask;

import java.time.OffsetDateTime;
import java.util.List;

public final class SubtitleViews {

    private SubtitleViews() {
    }

    public record Summary(long id, String name, SubtitleTask.Mode mode, SubtitleTask.State state,
                          SubtitleTask.State exportState, OffsetDateTime updatedAt) {
        public static Summary from(SubtitleTask task) {
            return new Summary(task.getId(), task.getSourceName(), task.getMode(), task.getState(),
                    task.getExportState(), task.getUpdatedAt());
        }
    }

    public record Detail(Summary task, long durationMs, String referenceTitle, int version,
                         List<Cue> cues, String error, String exportError) {
    }
}
