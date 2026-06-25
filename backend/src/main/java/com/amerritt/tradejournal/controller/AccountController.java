package com.amerritt.tradejournal.controller;

import com.amerritt.tradejournal.dto.AccountResponse;
import com.amerritt.tradejournal.security.AuthPrincipal;
import com.amerritt.tradejournal.service.AccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return accountService.listForUser(principal.userId());
    }
}
