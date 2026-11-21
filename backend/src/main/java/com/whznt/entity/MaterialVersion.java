package com.whznt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class MaterialVersion {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long materialId;
    private String filename;
    private String storageKey;
    private String contentType;
    private OffsetDateTime createdAt;
}
