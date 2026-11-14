package io.github.kadaferhidup.minibank.account;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(String accountNumber, AccountType type, String currency, AccountStatus status,
                              BigDecimal balance, Instant openedAt) {

    public static AccountResponse of(Account a) {
        return new AccountResponse(a.getAccountNumber(), a.getType(), a.getCurrency(), a.getStatus(), a.getBalance(),
                a.getCreatedAt());
    }
}
