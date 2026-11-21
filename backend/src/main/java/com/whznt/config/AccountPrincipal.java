package com.whznt.config;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

public final class AccountPrincipal extends User {

    private final long accountId;

    public AccountPrincipal(long accountId, String phone, String passwordHash) {
        super(phone, passwordHash, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.accountId = accountId;
    }

    public long accountId() {
        return accountId;
    }
}
