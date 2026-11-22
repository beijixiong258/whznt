package com.whznt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class PointCredit {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long accountId;
    private Long operatorId;
    private String requestId;
    private Long rechargeOrderId;
    private int points;
    private int balanceAfter;
    private String note;
    private OffsetDateTime createdAt;
}
