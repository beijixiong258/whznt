package com.whznt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Draft {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long currentVersionId;
}
