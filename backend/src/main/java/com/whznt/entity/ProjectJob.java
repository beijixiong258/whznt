package com.whznt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class ProjectJob {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String requestKey;
    private ProjectStep step;
    private String slot;
    private String inputSnapshot;
    private State state;
    private String error;
    private Long conversationId;
    private Long resultVersionId;
    private OffsetDateTime createdAt;

    public enum State { QUEUED, RUNNING, COMPLETED, FAILED }
}
