package io.github.kadaferhidup.minibank.account;

import io.github.kadaferhidup.minibank.account.AccountDtos.InquiryResponse;
import io.github.kadaferhidup.minibank.account.AccountDtos.StatementLine;
import io.github.kadaferhidup.minibank.account.AccountDtos.StatementResponse;
import io.github.kadaferhidup.minibank.common.ApiException;
import io.github.kadaferhidup.minibank.common.ErrorCode;
import io.github.kadaferhidup.minibank.common.Masking;
import io.github.kadaferhidup.minibank.config.BankProperties;
import io.github.kadaferhidup.minibank.ledger.BankTransaction;
import io.github.kadaferhidup.minibank.ledger.Direction;
import io.github.kadaferhidup.minibank.ledger.LedgerEntry;
import io.github.kadaferhidup.minibank.ledger.LedgerEntryRepository;
import io.github.kadaferhidup.minibank.user.User;
import io.github.kadaferhidup.minibank.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final int MAX_STATEMENT_DAYS = 90;

    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;
    private final AccountNumberGenerator numbers;
    private final UserRepository users;
    private final BankProperties props;
    private final Clock clock;

    public AccountService(AccountRepository accounts, LedgerEntryRepository entries, AccountNumberGenerator numbers,
                          UserRepository users, BankProperties props, Clock clock) {
        this.accounts = accounts;
        this.users = users;
        this.entries = entries;
        this.numbers = numbers;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public AccountResponse openFor(UUID userId) {
        return AccountResponse.of(open(users.getReferenceById(userId)));
    }

    @Transactional
    public Account open(User owner) {
        if (accounts.countByOwnerId(owner.getId()) >= props.limits().maxAccountsPerUser()) {
            throw new ApiException(ErrorCode.ACCOUNT_LIMIT_REACHED,
                    "Maksimal " + props.limits().maxAccountsPerUser() + " rekening per nasabah");
        }
        String number;
        do {
            number = numbers.next();
        } while (accounts.existsByAccountNumber(number));
        return accounts.save(Account.openSavings(owner, number));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listOwned(UUID userId) {
        return accounts.findByOwnerIdOrderByCreatedAt(userId).stream().map(AccountResponse::of).toList();
    }

    /**
     * Rekening milik pengguna. Kalau rekening milik orang lain, jawabannya sama dengan "tidak ditemukan"
     * supaya nomor rekening orang lain tidak bisa ditebak-tebak lewat API ini.
     */
    @Transactional(readOnly = true)
    public Account requireOwned(UUID userId, String accountNumber, boolean admin) {
        Account a = accounts.findByAccountNumber(accountNumber).orElse(null);
        if (a == null || a.isInternal() || (!admin && !a.isOwnedBy(userId))) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "Rekening tidak ditemukan");
        }
        return a;
    }

    @Transactional(readOnly = true)
    public InquiryResponse inquiry(String accountNumber) {
        if (!AccountNumberGenerator.isValid(accountNumber)) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "Nomor rekening tidak valid");
        }
        Account a = accounts.findByAccountNumber(accountNumber)
                .filter(x -> !x.isInternal())
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "Rekening tidak ditemukan"));
        if (!a.isActive()) {
            throw new ApiException(ErrorCode.DESTINATION_INVALID, "Rekening tujuan tidak dapat menerima dana");
        }
        return new InquiryResponse(a.getAccountNumber(), Masking.name(a.getOwner().getFullName()), a.getCurrency());
    }

    @Transactional(readOnly = true)
    public StatementResponse statement(UUID userId, boolean admin, String accountNumber, LocalDate from, LocalDate to,
                                       int page, int size) {
        Account account = requireOwned(userId, accountNumber, admin);
        ZoneId zone = clock.getZone();
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Tanggal awal harus sebelum tanggal akhir");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_STATEMENT_DAYS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Rentang mutasi maksimal " + MAX_STATEMENT_DAYS + " hari");
        }
        Instant fromTs = start.atStartOfDay(zone).toInstant();
        Instant toTs = end.plusDays(1).atStartOfDay(zone).toInstant();
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<LedgerEntry> result = entries.statement(account.getId(), fromTs, toTs, PageRequest.of(Math.max(page, 0), safeSize));
        List<StatementLine> lines = result.getContent().stream().map(AccountService::line).toList();
        return new StatementResponse(account.getAccountNumber(), account.getBalance(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), lines);
    }

    private static StatementLine line(LedgerEntry e) {
        BankTransaction t = e.getTransaction();
        Account other = e.getDirection() == Direction.DEBIT ? t.getDestAccount() : t.getSourceAccount();
        String counterparty = other == null ? null : other.isInternal() ? "TUNAI" : other.getAccountNumber();
        return new StatementLine(e.getCreatedAt(), t.getReference(), t.getType(), e.getDirection(), e.getAmount(),
                e.getBalanceAfter(), counterparty, t.getDescription());
    }
}
