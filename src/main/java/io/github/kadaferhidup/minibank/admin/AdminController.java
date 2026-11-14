package io.github.kadaferhidup.minibank.admin;

import io.github.kadaferhidup.minibank.account.AccountResponse;
import io.github.kadaferhidup.minibank.admin.AdminService.AccountDetail;
import io.github.kadaferhidup.minibank.admin.AdminService.AuditEntry;
import io.github.kadaferhidup.minibank.common.CurrentUser;
import io.github.kadaferhidup.minibank.ledger.LedgerVerificationService;
import io.github.kadaferhidup.minibank.ledger.TransactionResponse;
import io.github.kadaferhidup.minibank.transfer.TransferDtos.CashRequest;
import io.github.kadaferhidup.minibank.transfer.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint teller dan back office. Hanya bisa diakses role ADMIN. */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin / Teller")
public class AdminController {

    private final TransferService transfers;
    private final AdminService admin;
    private final LedgerVerificationService verification;

    public AdminController(TransferService transfers, AdminService admin, LedgerVerificationService verification) {
        this.transfers = transfers;
        this.admin = admin;
        this.verification = verification;
    }

    @PostMapping("/deposits")
    @Operation(summary = "Setor tunai ke rekening nasabah (wajib Idempotency-Key)")
    public ResponseEntity<TransactionResponse> deposit(@AuthenticationPrincipal Jwt jwt,
                                                       @RequestHeader("Idempotency-Key") String key,
                                                       @Valid @RequestBody CashRequest req, HttpServletRequest http) {
        var r = transfers.deposit(CurrentUser.id(jwt), key, req, CurrentUser.ip(http));
        return status(r.replayed()).header("Idempotent-Replayed", String.valueOf(r.replayed())).body(r.transaction());
    }

    @PostMapping("/withdrawals")
    @Operation(summary = "Tarik tunai dari rekening nasabah (wajib Idempotency-Key)")
    public ResponseEntity<TransactionResponse> withdraw(@AuthenticationPrincipal Jwt jwt,
                                                        @RequestHeader("Idempotency-Key") String key,
                                                        @Valid @RequestBody CashRequest req, HttpServletRequest http) {
        var r = transfers.withdraw(CurrentUser.id(jwt), key, req, CurrentUser.ip(http));
        return status(r.replayed()).header("Idempotent-Replayed", String.valueOf(r.replayed())).body(r.transaction());
    }

    @GetMapping("/accounts/{number}")
    @Operation(summary = "Detail rekening beserta data pemiliknya")
    public AccountDetail account(@PathVariable String number) {
        return admin.account(number);
    }

    @PostMapping("/accounts/{number}/freeze")
    @Operation(summary = "Bekukan rekening (misalnya dicurigai penipuan)")
    public AccountResponse freeze(@AuthenticationPrincipal Jwt jwt, @PathVariable String number,
                                  @Valid @RequestBody ReasonRequest req, HttpServletRequest http) {
        return admin.freeze(CurrentUser.id(jwt), number, req.reason(), CurrentUser.ip(http));
    }

    @PostMapping("/accounts/{number}/unfreeze")
    @Operation(summary = "Aktifkan kembali rekening yang dibekukan")
    public AccountResponse unfreeze(@AuthenticationPrincipal Jwt jwt, @PathVariable String number,
                                    @Valid @RequestBody ReasonRequest req, HttpServletRequest http) {
        return admin.unfreeze(CurrentUser.id(jwt), number, req.reason(), CurrentUser.ip(http));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Jejak audit terbaru")
    public PageResponse<AuditEntry> audit(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(admin.auditLogs(page, size));
    }

    @GetMapping("/ledger/verify")
    @Operation(summary = "Rekonsiliasi: cek buku besar seimbang dan saldo cocok dengan buku besar")
    public LedgerVerificationService.Report verify() {
        return verification.verify();
    }

    private static ResponseEntity.BodyBuilder status(boolean replayed) {
        return ResponseEntity.status(replayed ? HttpStatus.OK : HttpStatus.CREATED);
    }

    public record ReasonRequest(@NotBlank @Size(max = 200) String reason) {
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
        static <T> PageResponse<T> of(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }
}
