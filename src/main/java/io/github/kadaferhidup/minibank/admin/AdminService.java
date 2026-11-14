package io.github.kadaferhidup.minibank.admin;

import io.github.kadaferhidup.minibank.account.Account;
import io.github.kadaferhidup.minibank.account.AccountRepository;
import io.github.kadaferhidup.minibank.account.AccountResponse;
import io.github.kadaferhidup.minibank.audit.AuditLog;
import io.github.kadaferhidup.minibank.audit.AuditLogRepository;
import io.github.kadaferhidup.minibank.audit.AuditService;
import io.github.kadaferhidup.minibank.common.ApiException;
import io.github.kadaferhidup.minibank.common.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final AccountRepository accounts;
    private final AuditLogRepository auditLogs;
    private final AuditService audit;

    public AdminService(AccountRepository accounts, AuditLogRepository auditLogs, AuditService audit) {
        this.accounts = accounts;
        this.auditLogs = auditLogs;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AccountDetail account(String number) {
        Account a = customerAccount(number);
        return AccountDetail.of(a);
    }

    @Transactional
    public AccountResponse freeze(UUID adminId, String number, String reason, String ip) {
        Account a = customerAccount(number);
        a.freeze();
        audit.log(adminId, "ACCOUNT_FROZEN", number, ip, reason);
        return AccountResponse.of(a);
    }

    @Transactional
    public AccountResponse unfreeze(UUID adminId, String number, String reason, String ip) {
        Account a = customerAccount(number);
        a.unfreeze();
        audit.log(adminId, "ACCOUNT_UNFROZEN", number, ip, reason);
        return AccountResponse.of(a);
    }

    @Transactional(readOnly = true)
    public Page<AuditEntry> auditLogs(int page, int size) {
        return auditLogs.findAllByOrderByIdDesc(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))
                .map(AuditEntry::of);
    }

    private Account customerAccount(String number) {
        return accounts.findByAccountNumber(number)
                .filter(a -> !a.isInternal())
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "Rekening tidak ditemukan"));
    }

    public record AccountDetail(AccountResponse account, String ownerName, String ownerEmail, String ownerPhone) {
        static AccountDetail of(Account a) {
            return new AccountDetail(AccountResponse.of(a), a.getOwner().getFullName(), a.getOwner().getEmail(),
                    a.getOwner().getPhone());
        }
    }

    public record AuditEntry(Long id, UUID actorId, String action, String target, String ipAddress, String details,
                             Instant createdAt) {
        static AuditEntry of(AuditLog l) {
            return new AuditEntry(l.getId(), l.getActorId(), l.getAction(), l.getTarget(), l.getIpAddress(),
                    l.getDetails(), l.getCreatedAt());
        }
    }
}
