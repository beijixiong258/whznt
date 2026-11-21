package com.whznt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectVersionContext {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long projectId;
    private String snapshot;
}
