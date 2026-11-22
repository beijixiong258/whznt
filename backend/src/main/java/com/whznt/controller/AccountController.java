package com.whznt.controller;

import com.whznt.dto.WorkspaceRequests;
import com.whznt.service.AccountService;
import com.whznt.config.AccountPrincipal;
import com.whznt.vo.WorkspaceViews;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accounts;

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody WorkspaceRequests.Register request) {
        accounts.register(request.phone(), request.password());
    }

    @GetMapping("/me")
    public WorkspaceViews.Account me(@AuthenticationPrincipal AccountPrincipal principal) {
        return new WorkspaceViews.Account(principal.accountId(), principal.getUsername(), principal.role());
    }
}
