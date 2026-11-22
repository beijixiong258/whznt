package com.whznt.config;

import com.whznt.entity.Account;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

public final class AccountPrincipal extends User {

    private final long accountId;
    private final Account.Role role;
    private final int authVersion;

    public AccountPrincipal(Account account) {
        super(account.getPhone(), account.getPasswordHash(), account.isEnabled(), true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())));
        this.accountId = account.getId();
        this.role = account.getRole();
        this.authVersion = account.getAuthVersion();
    }

    public long accountId() {
        return accountId;
    }

    public Account.Role role() {
        return role;
    }

    public boolean remainsValid(Account account) {
        return account != null && account.isEnabled() && role == account.getRole()
                && authVersion == account.getAuthVersion();
    }
}
