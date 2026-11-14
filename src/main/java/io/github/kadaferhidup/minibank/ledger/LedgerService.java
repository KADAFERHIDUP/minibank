package io.github.kadaferhidup.minibank.ledger;

import io.github.kadaferhidup.minibank.account.Account;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Satu-satunya pintu untuk memindahkan uang. Setiap pemindahan ditulis sebagai jurnal double-entry:
 * satu baris DEBIT dan satu baris KREDIT dengan nominal yang sama, dalam transaksi database yang sama
 * dengan perubahan saldo. Rekening wajib sudah dikunci oleh pemanggil.
 */
@Service
public class LedgerService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");

    private final BankTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public LedgerService(BankTransactionRepository transactions, LedgerEntryRepository entries, Clock clock) {
        this.transactions = transactions;
        this.entries = entries;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BankTransaction post(UUID transactionId, TransactionType type, BigDecimal amount, String description,
                                Account debitAccount, Account creditAccount, UUID initiatedBy) {
        if (debitAccount.getId().equals(creditAccount.getId())) {
            throw new IllegalArgumentException("Debit dan kredit tidak boleh rekening yang sama");
        }
        Instant now = clock.instant();
        BigDecimal debitBalance = debitAccount.debit(amount);
        BigDecimal creditBalance = creditAccount.credit(amount);

        BankTransaction tx = transactions.save(new BankTransaction(transactionId, newReference(now), type, amount,
                description, debitAccount, creditAccount, initiatedBy, now));
        entries.save(new LedgerEntry(tx, debitAccount, Direction.DEBIT, amount, debitBalance, now));
        entries.save(new LedgerEntry(tx, creditAccount, Direction.CREDIT, amount, creditBalance, now));
        return tx;
    }

    String newReference(Instant now) {
        StringBuilder sb = new StringBuilder("TRX").append(DAY.format(now.atZone(clock.getZone())));
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
