package com.whznt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectArtifact {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long conversationId;
    private ProjectStep step;
    private String slot;
    private String topic;
}
