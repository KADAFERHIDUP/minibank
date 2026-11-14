package io.github.kadaferhidup.minibank.transfer;

import io.github.kadaferhidup.minibank.common.CurrentUser;
import io.github.kadaferhidup.minibank.ledger.TransactionResponse;
import io.github.kadaferhidup.minibank.transfer.IdempotencyService.Result;
import io.github.kadaferhidup.minibank.transfer.TransferDtos.TransferRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Transfer")
public class TransferController {

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    @PostMapping("/transfers")
    @Operation(summary = "Transfer antar rekening MiniBank",
            description = "Wajib header Idempotency-Key (misalnya UUID). Mengirim ulang kunci yang sama mengembalikan hasil "
                    + "transaksi pertama tanpa memotong saldo lagi.")
    public ResponseEntity<TransactionResponse> transfer(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Kunci unik per transaksi, contoh UUID") @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TransferRequest req,
            HttpServletRequest http) {
        return respond(service.transfer(CurrentUser.id(jwt), key, req, CurrentUser.ip(http)));
    }

    @GetMapping("/transactions/{reference}")
    @Operation(summary = "Detail transaksi berdasarkan nomor referensi")
    public TransactionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String reference) {
        return service.find(CurrentUser.id(jwt), CurrentUser.isAdmin(jwt), reference);
    }

    static ResponseEntity<TransactionResponse> respond(Result r) {
        return ResponseEntity.status(r.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(r.replayed()))
                .body(r.transaction());
    }
}
