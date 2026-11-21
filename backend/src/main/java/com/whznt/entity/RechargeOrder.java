package com.whznt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
public class RechargeOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private String requestKey;
    private String requestNumber;
    private BigDecimal amount;
    private String note;
    private String receiptKey;
    private String receiptSha256;
    private String receiptType;
    private OffsetDateTime createdAt;
}
