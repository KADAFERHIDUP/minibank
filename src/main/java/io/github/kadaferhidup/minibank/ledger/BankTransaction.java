package io.github.kadaferhidup.minibank.ledger;

import io.github.kadaferhidup.minibank.account.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Satu kejadian uang berpindah. Rincian debit/kreditnya ada di LedgerEntry. */
@Entity
@Table(name = "transactions")
public class BankTransaction {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 24)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dest_account_id")
    private Account destAccount;

    @Column(name = "initiated_by", nullable = false)
    private UUID initiatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BankTransaction() {
    }

    BankTransaction(UUID id, String reference, TransactionType type, BigDecimal amount, String description,
                    Account source, Account dest, UUID initiatedBy, Instant createdAt) {
        this.id = id;
        this.reference = reference;
        this.type = type;
        this.status = "COMPLETED";
        this.amount = amount;
        this.description = description;
        this.sourceAccount = source;
        this.destAccount = dest;
        this.initiatedBy = initiatedBy;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public TransactionType getType() { return type; }
    public String getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public Account getSourceAccount() { return sourceAccount; }
    public Account getDestAccount() { return destAccount; }
    public UUID getInitiatedBy() { return initiatedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
