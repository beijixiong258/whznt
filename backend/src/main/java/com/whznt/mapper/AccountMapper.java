package com.whznt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whznt.entity.Account;
import org.apache.ibatis.annotations.Select;

public interface AccountMapper extends BaseMapper<Account> {

    @Select("SELECT * FROM account WHERE id = #{id} FOR UPDATE")
    Account lock(long id);
}
