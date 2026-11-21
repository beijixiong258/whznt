package com.whznt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name;
    private Kind kind;
    private Long currentVersionId;

    public enum Kind { LIFE, BUSINESS, OBSERVATION, STYLE }
}
