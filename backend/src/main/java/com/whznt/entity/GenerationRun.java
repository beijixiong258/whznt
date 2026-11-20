package com.whznt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class GenerationRun {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private String requestKey;
    private Long conversationId;
    private Kind kind;
    private State state;
    private String inputSnapshot;
    private Long versionId;
    private OffsetDateTime startedAt;

    public enum Kind {
        WRITE, REGENERATE, CHAT
    }

    public enum State {
        RUNNING, COMPLETED, FAILED
    }
}
