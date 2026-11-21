package com.whznt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class SubtitleTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private String sourceKey;
    private String sourceName;
    private Long durationMs;
    private Mode mode;
    private Long draftVersionId;
    private String referenceTitle;
    private String referenceText;
    private State state;
    private String error;
    private String originalTimeline;
    private String timeline;
    private Integer version;
    private State exportState;
    private String exportError;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public enum Mode {
        AUTO, ALIGN, IMPORT
    }

    public enum State {
        NONE, QUEUED, RUNNING, READY, FAILED;

        public boolean pending() {
            return this == QUEUED || this == RUNNING;
        }
    }
}
