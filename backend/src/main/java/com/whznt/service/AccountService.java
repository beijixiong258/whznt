package com.whznt.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.whznt.entity.Account;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface AccountService extends IService<Account>, UserDetailsService {

    void register(String phone, String password);
}
