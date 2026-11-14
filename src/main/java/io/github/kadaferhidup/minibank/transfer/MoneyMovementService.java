package io.github.kadaferhidup.minibank.transfer;

import io.github.kadaferhidup.minibank.account.Account;
import io.github.kadaferhidup.minibank.account.AccountRepository;
import io.github.kadaferhidup.minibank.account.AccountType;
import io.github.kadaferhidup.minibank.audit.AuditService;
import io.github.kadaferhidup.minibank.common.ApiException;
import io.github.kadaferhidup.minibank.common.ErrorCode;
import io.github.kadaferhidup.minibank.common.Money;
import io.github.kadaferhidup.minibank.config.BankProperties;
import io.github.kadaferhidup.minibank.ledger.BankTransaction;
import io.github.kadaferhidup.minibank.ledger.BankTransactionRepository;
import io.github.kadaferhidup.minibank.ledger.LedgerService;
import io.github.kadaferhidup.minibank.ledger.TransactionResponse;
import io.github.kadaferhidup.minibank.ledger.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inti perpindahan uang. Tiap method berjalan dalam SATU transaksi database:
 * klaim idempotency key, kunci rekening, validasi aturan, tulis jurnal, ubah saldo, catat audit.
 * Kalau satu langkah gagal, semuanya dibatalkan, tidak ada uang yang setengah terkirim.
 */
@Service
public class MoneyMovementService {

    private static final BigDecimal MAX_CASH = new BigDecimal("1000000000.00");

    private final AccountRepository accounts;
    private final BankTransactionRepository transactions;
    private final LedgerService ledger;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final BankProperties props;
    private final Clock clock;

    public MoneyMovementService(AccountRepository accounts, BankTransactionRepository transactions, LedgerService ledger,
                                IdempotencyService idempotency, AuditService audit, BankProperties props, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.ledger = ledger;
        this.idempotency = idempotency;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public TransactionResponse transfer(UUID txId, UUID userId, String key, String requestHash,
                                        String fromNumber, String toNumber, BigDecimal rawAmount, String description, String ip) {
        idempotency.claim(userId, key, requestHash, txId);
        BigDecimal amount = Money.normalize(rawAmount);
        BankProperties.Limits limits = props.limits();
        if (amount.compareTo(limits.minTransfer()) < 0) {
            throw new ApiException(ErrorCode.INVALID_AMOUNT, "Minimal transfer " + Money.rupiah(limits.minTransfer()));
        }
        if (amount.compareTo(limits.maxPerTransaction()) > 0) {
            throw new ApiException(ErrorCode.LIMIT_EXCEEDED,
                    "Maksimal transfer per transaksi " + Money.rupiah(limits.maxPerTransaction()));
        }
        if (fromNumber.equals(toNumber)) {
            throw new ApiException(ErrorCode.SAME_ACCOUNT, "Rekening asal dan tujuan tidak boleh sama");
        }
        UUID fromId = accounts.findIdByAccountNumber(fromNumber)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "Rekening asal tidak ditemukan"));
        UUID toId = accounts.findIdByAccountNumber(toNumber)
                .orElseThrow(() -> new ApiException(ErrorCode.DESTINATION_INVALID, "Rekening tujuan tidak ditemukan"));

        Locked pair = lock(fromId, toId);
        Account from = pair.first();
        Account to = pair.second();

        if (from.isInternal() || !from.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "Rekening asal tidak ditemukan");
        }
        if (!from.isActive()) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_ACTIVE, "Rekening asal sedang dibekukan atau ditutup");
        }
        if (to.isInternal() || !to.isActive()) {
            throw new ApiException(ErrorCode.DESTINATION_INVALID, "Rekening tujuan tidak dapat menerima dana");
        }
        // Dihitung setelah rekening asal dikunci, jadi transfer paralel dari rekening yang sama tidak bisa lolos limit.
        BigDecimal usedToday = transactions.sumOutgoingSince(from.getId(), TransactionType.TRANSFER, startOfToday());
        if (usedToday.add(amount).compareTo(limits.dailyTransfer()) > 0) {
            BigDecimal left = limits.dailyTransfer().subtract(usedToday).max(BigDecimal.ZERO);
            throw new ApiException(ErrorCode.DAILY_LIMIT_EXCEEDED,
                    "Melebihi limit transfer harian. Sisa limit hari ini " + Money.rupiah(left));
        }

        BankTransaction tx = ledger.post(txId, TransactionType.TRANSFER, amount, clean(description), from, to, userId);
        audit.log(userId, "TRANSFER", tx.getReference(), ip,
                from.getAccountNumber() + " -> " + to.getAccountNumber() + " " + amount.toPlainString());
        return TransactionResponse.of(tx);
    }

    /** Setor tunai lewat teller: kas bank didebit, rekening nasabah dikredit. */
    @Transactional
    public TransactionResponse deposit(UUID txId, UUID tellerId, String key, String requestHash,
                                       String accountNumber, BigDecimal rawAmount, String description, String ip) {
        idempotency.claim(tellerId, key, requestHash, txId);
        BigDecimal amount = cashAmount(rawAmount);
        UUID customerId = accounts.findIdByAccountNumber(accountNumber)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "Rekening tidak ditemukan"));
        Locked pair = lock(vaultId(), customerId);
        Account vault = pair.first();
        Account customer = pair.second();
        requireCustomerActive(customer);
        BankTransaction tx = ledger.post(txId, TransactionType.DEPOSIT, amount,
                description == null ? "Setor tunai" : clean(description), vault, customer, tellerId);
        audit.log(tellerId, "CASH_DEPOSIT", tx.getReference(), ip, customer.getAccountNumber() + " " + amount.toPlainString());
        return TransactionResponse.of(tx);
    }

    /** Tarik tunai lewat teller: rekening nasabah didebit, kas bank dikredit. */
    @Transactional
    public TransactionResponse withdraw(UUID txId, UUID tellerId, String key, String requestHash,
                                        String accountNumber, BigDecimal rawAmount, String description, String ip) {
        idempotency.claim(tellerId, key, requestHash, txId);
        BigDecimal amount = cashAmount(rawAmount);
        UUID customerId = accounts.findIdByAccountNumber(accountNumber)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "Rekening tidak ditemukan"));
        Locked pair = lock(customerId, vaultId());
        Account customer = pair.first();
        Account vault = pair.second();
        requireCustomerActive(customer);
        BankTransaction tx = ledger.post(txId, TransactionType.WITHDRAWAL, amount,
                description == null ? "Tarik tunai" : clean(description), customer, vault, tellerId);
        audit.log(tellerId, "CASH_WITHDRAWAL", tx.getReference(), ip, customer.getAccountNumber() + " " + amount.toPlainString());
        return TransactionResponse.of(tx);
    }

    /** Mengunci dua rekening sekaligus dan mengembalikannya sesuai urutan argumen. */
    private Locked lock(UUID firstId, UUID secondId) {
        List<Account> rows = accounts.lockAllOrdered(List.of(firstId, secondId));
        Account first = rows.stream().filter(a -> a.getId().equals(firstId)).findFirst().orElseThrow();
        Account second = rows.stream().filter(a -> a.getId().equals(secondId)).findFirst().orElseThrow();
        return new Locked(first, second);
    }

    private UUID vaultId() {
        return accounts.findIdsByType(AccountType.INTERNAL).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Rekening kas internal belum ada"));
    }

    private static void requireCustomerActive(Account a) {
        if (a.isInternal()) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "Rekening tidak ditemukan");
        }
        if (!a.isActive()) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_ACTIVE, "Rekening sedang dibekukan atau ditutup");
        }
    }

    private static BigDecimal cashAmount(BigDecimal raw) {
        BigDecimal amount = Money.normalize(raw);
        if (amount.compareTo(MAX_CASH) > 0) {
            throw new ApiException(ErrorCode.LIMIT_EXCEEDED, "Transaksi tunai maksimal " + Money.rupiah(MAX_CASH));
        }
        return amount;
    }

    private Instant startOfToday() {
        return LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
    }

    private static String clean(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String d = description.strip().replaceAll("\\s+", " ");
        return d.length() > 140 ? d.substring(0, 140) : d;
    }

    private record Locked(Account first, Account second) {}
}
