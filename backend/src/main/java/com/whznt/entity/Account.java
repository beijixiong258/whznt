package com.whznt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Account {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String phone;
    private String username;
    private String passwordHash;
    private Role role;
    private boolean enabled;
    private int authVersion;
    private int points;

    public enum Role { USER, ADMIN }
}
