package com.whznt.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.whznt.config.AccountPrincipal;
import com.whznt.dto.AdminRequests;
import com.whznt.entity.Account;
import com.whznt.vo.AdminViews;
import com.whznt.vo.WorkspaceViews;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;
import java.util.UUID;

public interface AccountService extends IService<Account>, UserDetailsService {

    @Override
    AccountPrincipal loadUserByUsername(String username);

    void register(String phone, String password);
    WorkspaceViews.Account profile(long id);
    WorkspaceViews.Account updateUsername(long id, String username);
    AdminViews.User createUser(String phone, String password);
    AdminViews.Page<AdminViews.User> users(String phone, int page);
    AdminViews.User updateUser(long id, AdminRequests.UpdateUser input);
    void disableUser(long id);
    List<AdminViews.Credit> credits(long id);
    AdminViews.Credit addPoints(long id, long operatorId, UUID requestId, Long orderId, int points, String note);
}
