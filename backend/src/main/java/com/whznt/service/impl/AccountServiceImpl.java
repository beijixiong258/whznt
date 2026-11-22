package com.whznt.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.whznt.config.AccountPrincipal;
import com.whznt.dto.AdminRequests;
import com.whznt.entity.Account;
import com.whznt.entity.PointCredit;
import com.whznt.exception.WorkspaceException;
import com.whznt.mapper.AccountMapper;
import com.whznt.mapper.PointCreditMapper;
import com.whznt.service.AccountService;
import com.whznt.vo.AdminViews;
import com.whznt.vo.WorkspaceViews;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements AccountService {

    private static final int PAGE_SIZE = 50;
    private static final String ADMIN_USERNAME = "管理员北极熊";
    private final PasswordEncoder passwordEncoder;
    private final PointCreditMapper pointCredits;

    @Override
    public void register(String phone, String password) {
        createUser(phone, password);
    }

    @Override
    public WorkspaceViews.Account profile(long id) {
        return profileView(requireAccount(getById(id)));
    }

    @Override
    @Transactional
    public WorkspaceViews.Account updateUsername(long id, String input) {
        Account account = requireAccount(baseMapper.lock(id));
        if (account.getRole() != Account.Role.USER) {
            throw new WorkspaceException(HttpStatus.FORBIDDEN, "管理员的用户名固定为管理员北极熊。");
        }
        String username = input.strip();
        int width = username.codePoints().map(value -> value < 128 ? 1 : 2).sum();
        if (username.codePoints().anyMatch(Character::isISOControl) || width > 20) {
            throw WorkspaceException.badRequest("用户名须为单行，最多 10 个汉字或 20 个英文字母。");
        }
        account.setUsername(username);
        updateById(account);
        return profileView(account);
    }

    private static Account requireAccount(Account account) {
        if (account == null || !account.isEnabled()) {
            throw new WorkspaceException(HttpStatus.UNAUTHORIZED, "请登录后继续。");
        }
        return account;
    }

    private static WorkspaceViews.Account profileView(Account account) {
        String username = account.getRole() == Account.Role.ADMIN ? ADMIN_USERNAME : account.getUsername();
        return new WorkspaceViews.Account(account.getId(), account.getPhone(), username, account.getRole());
    }

    @Override
    public AdminViews.User createUser(String phone, String password) {
        Account account = new Account();
        account.setPhone(validPhone(phone));
        account.setUsername("");
        account.setPasswordHash(encodePassword(password));
        account.setRole(Account.Role.USER);
        account.setEnabled(true);
        try {
            save(account);
        } catch (DuplicateKeyException exception) {
            throw WorkspaceException.conflict("这个手机号已注册，可以直接登录。");
        }
        return AdminViews.User.from(account);
    }

    @Override
    public AdminViews.Page<AdminViews.User> users(String phone, int page) {
        List<Account> rows = lambdaQuery().eq(Account::getRole, Account.Role.USER)
                .like(!phone.isBlank(), Account::getPhone, phone.strip())
                .orderByDesc(Account::getId)
                .last("LIMIT " + (PAGE_SIZE + 1) + " OFFSET " + (long) (page - 1) * PAGE_SIZE).list();
        return new AdminViews.Page<>(rows.stream().limit(PAGE_SIZE).map(AdminViews.User::from).toList(),
                rows.size() > PAGE_SIZE);
    }

    @Override
    @Transactional
    public AdminViews.User updateUser(long id, AdminRequests.UpdateUser input) {
        Account account = ordinaryUser(baseMapper.lock(id));
        String phone = input.phone() == null ? account.getPhone() : validPhone(input.phone());
        boolean enabled = input.enabled() == null ? account.isEnabled() : input.enabled();
        if (account.isEnabled() == enabled && account.getPhone().equals(phone) && input.password() == null) {
            return AdminViews.User.from(account);
        }
        account.setAuthVersion(account.getAuthVersion() + 1);
        account.setEnabled(enabled);
        account.setPhone(phone);
        if (input.password() != null) account.setPasswordHash(encodePassword(input.password()));
        try {
            updateById(account);
        } catch (DuplicateKeyException exception) {
            throw WorkspaceException.conflict("这个手机号已被占用。");
        }
        return AdminViews.User.from(account);
    }

    @Override
    @Transactional
    public void disableUser(long id) {
        updateUser(id, new AdminRequests.UpdateUser(null, null, false));
    }

    @Override
    public List<AdminViews.Credit> credits(long id) {
        ordinaryUser(getById(id));
        return pointCredits.selectList(Wrappers.<PointCredit>lambdaQuery().eq(PointCredit::getAccountId, id)
                .orderByDesc(PointCredit::getId).last("LIMIT 50"))
                .stream().map(AdminViews.Credit::from).toList();
    }

    @Override
    @Transactional
    public AdminViews.Credit addPoints(long id, long operatorId, UUID requestId, Long orderId, int points, String note) {
        Account account = baseMapper.lock(id);
        if (orderId == null) ordinaryUser(account);
        if (account == null) throw WorkspaceException.notFound("找不到该充值账号。");
        String key = requestId == null ? null : requestId.toString();
        if (key != null) {
            PointCredit prior = pointCredits.selectOne(Wrappers.<PointCredit>lambdaQuery()
                    .eq(PointCredit::getAccountId, id).eq(PointCredit::getRequestId, key));
            if (prior != null) {
                if (prior.getPoints() != points || !prior.getNote().equals(note)) {
                    throw WorkspaceException.conflict("这次入账已提交，请刷新后重试。");
                }
                return AdminViews.Credit.from(prior);
            }
        }
        if (points <= 0 || points > Integer.MAX_VALUE - account.getPoints()) {
            throw WorkspaceException.badRequest("请输入有效的积分数量。");
        }
        account.setPoints(account.getPoints() + points);
        updateById(account);

        PointCredit credit = new PointCredit();
        credit.setAccountId(id);
        credit.setOperatorId(operatorId);
        credit.setRequestId(key);
        credit.setRechargeOrderId(orderId);
        credit.setPoints(points);
        credit.setBalanceAfter(account.getPoints());
        credit.setNote(note);
        credit.setCreatedAt(OffsetDateTime.now());
        pointCredits.insert(credit);
        return AdminViews.Credit.from(credit);
    }

    private static Account ordinaryUser(Account account) {
        if (account == null || account.getRole() != Account.Role.USER) {
            throw WorkspaceException.notFound("找不到这个普通用户。");
        }
        return account;
    }

    private static String validPhone(String phone) {
        String normalizedPhone = phone.strip();
        if (!normalizedPhone.matches("1[3-9][0-9]{9}")) {
            throw WorkspaceException.badRequest("请输入 11 位大陆手机号。");
        }
        return normalizedPhone;
    }

    private String encodePassword(String password) {
        int length = password.codePointCount(0, password.length());
        if (length < 6 || length > 20) {
            throw WorkspaceException.badRequest("密码须为 6-20 个字符。");
        }

        return passwordEncoder.encode(password);
    }

    @Override
    public AccountPrincipal loadUserByUsername(String username) {
        Account account = lambdaQuery().eq(Account::getPhone, username.strip()).one();
        if (account == null) {
            throw new UsernameNotFoundException("手机号或密码有误。");
        }
        return new AccountPrincipal(account);
    }
}
