package io.github.kadaferhidup.minibank.account;

import io.github.kadaferhidup.minibank.account.AccountDtos.InquiryResponse;
import io.github.kadaferhidup.minibank.account.AccountDtos.StatementResponse;
import io.github.kadaferhidup.minibank.common.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Rekening")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Daftar rekening milik saya")
    public List<AccountResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.listOwned(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Buka rekening tabungan tambahan")
    public AccountResponse open(@AuthenticationPrincipal Jwt jwt) {
        return service.openFor(CurrentUser.id(jwt));
    }

    @GetMapping("/{number}")
    @Operation(summary = "Detail dan saldo rekening saya")
    public AccountResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String number) {
        return AccountResponse.of(service.requireOwned(CurrentUser.id(jwt), number, CurrentUser.isAdmin(jwt)));
    }

    @GetMapping("/{number}/inquiry")
    @Operation(summary = "Cek nama pemilik rekening tujuan sebelum transfer")
    public InquiryResponse inquiry(@PathVariable String number) {
        return service.inquiry(number);
    }

    @GetMapping("/{number}/statement")
    @Operation(summary = "Mutasi rekening (maksimal 90 hari, default 30 hari terakhir)")
    public StatementResponse statement(@AuthenticationPrincipal Jwt jwt, @PathVariable String number,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.statement(CurrentUser.id(jwt), CurrentUser.isAdmin(jwt), number, from, to, page, size);
    }
}
