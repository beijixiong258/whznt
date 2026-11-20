package com.whznt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Role role;
    private String content;
    private OffsetDateTime createdAt;

    public enum Role {
        USER, ASSISTANT
    }
}
