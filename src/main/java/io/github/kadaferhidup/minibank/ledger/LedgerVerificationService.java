package io.github.kadaferhidup.minibank.ledger;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pemeriksaan rekonsiliasi: buku besar harus seimbang dan saldo rekening harus cocok dengan buku besar. */
@Service
public class LedgerVerificationService {

    private final LedgerEntryRepository entries;

    public LedgerVerificationService(LedgerEntryRepository entries) {
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public Report verify() {
        Object[] totals = entries.totals().get(0);
        BigDecimal debit = (BigDecimal) totals[0];
        BigDecimal credit = (BigDecimal) totals[1];
        List<Mismatch> mismatches = entries.mismatchedAccounts().stream()
                .map(r -> new Mismatch((String) r[0], (BigDecimal) r[1], (BigDecimal) r[2]))
                .toList();
        List<String> unbalanced = entries.unbalancedTransactions();
        boolean ok = debit.compareTo(credit) == 0 && mismatches.isEmpty() && unbalanced.isEmpty();
        return new Report(ok, debit, credit, mismatches, unbalanced);
    }

    public record Report(boolean balanced, BigDecimal totalDebit, BigDecimal totalCredit,
                         List<Mismatch> accountMismatches, List<String> unbalancedTransactions) {}

    public record Mismatch(String accountNumber, BigDecimal storedBalance, BigDecimal ledgerBalance) {}
}
