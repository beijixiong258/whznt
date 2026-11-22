package com.whznt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class PersonProject {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private String name;
    private String summary;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}
