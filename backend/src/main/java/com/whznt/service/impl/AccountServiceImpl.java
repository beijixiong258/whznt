package com.whznt.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.whznt.config.AccountPrincipal;
import com.whznt.entity.Account;
import com.whznt.exception.WorkspaceException;
import com.whznt.mapper.AccountMapper;
import com.whznt.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements AccountService {

    private final PasswordEncoder passwordEncoder;

    @Override
    public void register(String phone, String password) {
        String normalizedPhone = phone.strip();
        if (!normalizedPhone.matches("1[3-9][0-9]{9}")) {
            throw WorkspaceException.badRequest("请输入 11 位大陆手机号。");
        }
        int length = password.codePointCount(0, password.length());
        if (length < 6 || length > 20) {
            throw WorkspaceException.badRequest("密码须为 6-20 个字符。");
        }

        Account account = new Account();
        account.setPhone(normalizedPhone);
        account.setPasswordHash(passwordEncoder.encode(password));
        try {
            save(account);
        } catch (DuplicateKeyException exception) {
            throw WorkspaceException.conflict("这个手机号已注册，可以直接登录。");
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        Account account = lambdaQuery().eq(Account::getPhone, username.strip()).one();
        if (account == null) {
            throw new UsernameNotFoundException("手机号或密码有误。");
        }
        return new AccountPrincipal(account.getId(), account.getPhone(), account.getPasswordHash());
    }
}
