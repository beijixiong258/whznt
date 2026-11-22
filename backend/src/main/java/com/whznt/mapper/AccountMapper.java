package com.whznt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whznt.entity.Account;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AccountMapper extends BaseMapper<Account> {

    @Select("SELECT * FROM account WHERE id = #{id} FOR UPDATE")
    Account lock(long id);

    @Insert("""
            INSERT INTO account (phone, password_hash, role, username)
            SELECT #{phone}, #{passwordHash}, 'ADMIN', #{username}
            WHERE NOT EXISTS (SELECT 1 FROM account WHERE phone = #{phone})
            ON CONFLICT (phone) DO NOTHING
            """)
    int insertBootstrapAdmin(@Param("phone") String phone, @Param("passwordHash") String passwordHash,
                             @Param("username") String username);
}
