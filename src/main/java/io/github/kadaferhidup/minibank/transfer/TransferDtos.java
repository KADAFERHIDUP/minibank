package io.github.kadaferhidup.minibank.transfer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class TransferDtos {

    private TransferDtos() {
    }

    public record TransferRequest(
            @NotBlank @Pattern(regexp = "^\\d{10}$", message = "nomor rekening 10 digit") String fromAccount,
            @NotBlank @Pattern(regexp = "^\\d{10}$", message = "nomor rekening 10 digit") String toAccount,
            @NotNull @Positive BigDecimal amount,
            @Size(max = 140) String description,
            @NotBlank String pin) {
    }

    public record CashRequest(
            @NotBlank @Pattern(regexp = "^\\d{10}$", message = "nomor rekening 10 digit") String accountNumber,
            @NotNull @Positive BigDecimal amount,
            @Size(max = 140) String description) {
    }
}
