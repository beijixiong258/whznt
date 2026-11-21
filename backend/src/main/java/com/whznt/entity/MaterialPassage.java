package com.whznt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MaterialPassage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long versionId;
    private Integer ordinal;
    private String location;
    private String content;
}
