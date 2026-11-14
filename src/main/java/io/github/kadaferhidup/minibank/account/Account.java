package io.github.kadaferhidup.minibank.account;

import io.github.kadaferhidup.minibank.common.ApiException;
import io.github.kadaferhidup.minibank.common.ErrorCode;
import io.github.kadaferhidup.minibank.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(name = "account_number", nullable = false, unique = true, length = 10)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
    }

    public static Account openSavings(User owner, String accountNumber) {
        Account a = new Account();
        a.id = UUID.randomUUID();
        a.accountNumber = accountNumber;
        a.owner = owner;
        a.type = AccountType.SAVINGS;
        a.currency = "IDR";
        a.status = AccountStatus.ACTIVE;
        a.balance = BigDecimal.ZERO.setScale(2);
        a.createdAt = Instant.now();
        return a;
    }

    public boolean isInternal() {
        return type == AccountType.INTERNAL;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isOwnedBy(UUID userId) {
        return owner != null && owner.getId().equals(userId);
    }

    /** Debit mengurangi saldo. Rekening nasabah tidak boleh minus. */
    public BigDecimal debit(BigDecimal amount) {
        if (!isInternal() && balance.compareTo(amount) < 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE, "Saldo tidak cukup");
        }
        balance = balance.subtract(amount);
        return balance;
    }

    /** Kredit menambah saldo. */
    public BigDecimal credit(BigDecimal amount) {
        balance = balance.add(amount);
        return balance;
    }

    public void freeze() {
        if (status != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_STATE, "Hanya rekening aktif yang bisa dibekukan");
        }
        status = AccountStatus.FROZEN;
    }

    public void unfreeze() {
        if (status != AccountStatus.FROZEN) {
            throw new ApiException(ErrorCode.INVALID_STATE, "Rekening tidak sedang dibekukan");
        }
        status = AccountStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public User getOwner() { return owner; }
    public AccountType getType() { return type; }
    public String getCurrency() { return currency; }
    public AccountStatus getStatus() { return status; }
    public BigDecimal getBalance() { return balance; }
    public Instant getCreatedAt() { return createdAt; }
}
