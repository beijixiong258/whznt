package com.whznt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class DraftVersion {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long draftId;
    private Long baseVersionId;
    private String title;
    private String content;
    private Source source;
    private String writingInput;
    private String model;
    private OffsetDateTime createdAt;

    public enum Source {
        GENERATED, EDITED
    }
}
