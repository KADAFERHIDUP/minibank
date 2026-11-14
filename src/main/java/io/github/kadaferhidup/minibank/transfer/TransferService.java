package io.github.kadaferhidup.minibank.transfer;

import io.github.kadaferhidup.minibank.auth.PinService;
import io.github.kadaferhidup.minibank.common.ApiException;
import io.github.kadaferhidup.minibank.common.ErrorCode;
import io.github.kadaferhidup.minibank.ledger.BankTransaction;
import io.github.kadaferhidup.minibank.ledger.BankTransactionRepository;
import io.github.kadaferhidup.minibank.ledger.TransactionResponse;
import io.github.kadaferhidup.minibank.transfer.IdempotencyService.Result;
import io.github.kadaferhidup.minibank.transfer.TransferDtos.CashRequest;
import io.github.kadaferhidup.minibank.transfer.TransferDtos.TransferRequest;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Merangkai PIN, idempotency, dan perpindahan uang. Sengaja tidak @Transactional di sini. */
@Service
public class TransferService {

    private final PinService pins;
    private final IdempotencyService idempotency;
    private final MoneyMovementService money;
    private final BankTransactionRepository transactions;

    public TransferService(PinService pins, IdempotencyService idempotency, MoneyMovementService money,
                           BankTransactionRepository transactions) {
        this.pins = pins;
        this.idempotency = idempotency;
        this.money = money;
        this.transactions = transactions;
    }

    public Result transfer(UUID userId, String key, TransferRequest req, String ip) {
        pins.verify(userId, req.pin(), ip);
        String hash = IdempotencyService.hash("TRANSFER", req.fromAccount(), req.toAccount(), plain(req.amount()),
                String.valueOf(req.description()));
        return idempotency.execute(userId, key, hash, txId -> money.transfer(txId, userId, key, hash,
                req.fromAccount(), req.toAccount(), req.amount(), req.description(), ip));
    }

    public Result deposit(UUID tellerId, String key, CashRequest req, String ip) {
        String hash = IdempotencyService.hash("DEPOSIT", req.accountNumber(), plain(req.amount()), String.valueOf(req.description()));
        return idempotency.execute(tellerId, key, hash, txId -> money.deposit(txId, tellerId, key, hash,
                req.accountNumber(), req.amount(), req.description(), ip));
    }

    public Result withdraw(UUID tellerId, String key, CashRequest req, String ip) {
        String hash = IdempotencyService.hash("WITHDRAWAL", req.accountNumber(), plain(req.amount()), String.valueOf(req.description()));
        return idempotency.execute(tellerId, key, hash, txId -> money.withdraw(txId, tellerId, key, hash,
                req.accountNumber(), req.amount(), req.description(), ip));
    }

    /** Nasabah hanya boleh melihat transaksi yang melibatkan rekeningnya sendiri. */
    @Transactional(readOnly = true)
    public TransactionResponse find(UUID userId, boolean admin, String reference) {
        BankTransaction t = transactions.findByReference(reference)
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaksi tidak ditemukan"));
        boolean involved = (t.getSourceAccount() != null && t.getSourceAccount().isOwnedBy(userId))
                || (t.getDestAccount() != null && t.getDestAccount().isOwnedBy(userId));
        if (!admin && !involved) {
            throw new ApiException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaksi tidak ditemukan");
        }
        return TransactionResponse.of(t);
    }

    private static String plain(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
