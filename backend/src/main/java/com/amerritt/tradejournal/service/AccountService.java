package com.amerritt.tradejournal.service;

import com.amerritt.tradejournal.dto.AccountResponse;
import com.amerritt.tradejournal.exception.ResourceNotFoundException;
import com.amerritt.tradejournal.model.Account;
import com.amerritt.tradejournal.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<Long> accountIdsForUser(Long userId) {
        return accountRepository.findByUserId(userId).stream().map(Account::getId).toList();
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listForUser(Long userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(AccountResponse::from)
                .toList();
    }

    /** Resolves an account the user owns, or throws 404 (never reveals others' account ids). */
    @Transactional(readOnly = true)
    public Account requireOwned(Long accountId, Long userId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account " + accountId + " not found for current user"));
    }
}
