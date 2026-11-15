package io.github.kadaferhidup.minibank.account;

import io.github.kadaferhidup.minibank.ledger.Direction;
import io.github.kadaferhidup.minibank.ledger.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AccountDtos {

    private AccountDtos() {
    }

    /** Hasil cek nama penerima: nama disamarkan sebagian. */
    public record InquiryResponse(String accountNumber, String holderName, String currency) {
    }

    public record StatementLine(Instant date, String reference, TransactionType type, Direction direction,
                                BigDecimal amount, BigDecimal balanceAfter, String counterparty, String counterpartyName,
                                String description) {
    }

    public record StatementResponse(String accountNumber, BigDecimal currentBalance, int page, int size,
                                    long totalItems, int totalPages, List<StatementLine> items) {
    }
}
