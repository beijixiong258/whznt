package com.whznt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private Long projectId;
    private Kind kind;
    private String title;
    private OffsetDateTime updatedAt;

    public enum Kind {
        WRITING, CHAT
    }
}
