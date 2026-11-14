package io.github.kadaferhidup.minibank.ledger;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        String reference,
        TransactionType type,
        String status,
        BigDecimal amount,
        String description,
        String sourceAccount,
        String destinationAccount,
        Instant createdAt) {

    /** Rekening internal bank (kas) ditampilkan sebagai "TUNAI" ke nasabah. */
    public static TransactionResponse of(BankTransaction t) {
        return new TransactionResponse(t.getReference(), t.getType(), t.getStatus(), t.getAmount(), t.getDescription(),
                label(t.getSourceAccount()), label(t.getDestAccount()), t.getCreatedAt());
    }

    private static String label(io.github.kadaferhidup.minibank.account.Account a) {
        if (a == null) {
            return null;
        }
        return a.isInternal() ? "TUNAI" : a.getAccountNumber();
    }
}
