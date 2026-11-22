package com.whznt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whznt.entity.RechargeOrder;
import org.apache.ibatis.annotations.Select;

public interface RechargeOrderMapper extends BaseMapper<RechargeOrder> {

    @Select("SELECT * FROM recharge_order WHERE id = #{id} FOR UPDATE")
    RechargeOrder lock(long id);
}
