package io.github.kadaferhidup.minibank.transfer;

import io.github.kadaferhidup.minibank.common.ApiException;
import io.github.kadaferhidup.minibank.common.ErrorCode;
import io.github.kadaferhidup.minibank.ledger.BankTransactionRepository;
import io.github.kadaferhidup.minibank.ledger.TransactionResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Menjamin satu Idempotency-Key hanya menghasilkan satu transaksi.
 *
 * Alurnya: kalau kunci sudah pernah dipakai, kembalikan hasil lama (replay). Kalau belum, jalankan transaksi;
 * di dalam transaksi itu kunci diklaim lebih dulu lewat unique constraint (user_id, idem_key).
 * Dua permintaan kembar yang datang bersamaan akan antre di constraint itu: yang kedua gagal dengan
 * pelanggaran unique, lalu di sini diubah menjadi replay hasil permintaan pertama.
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository keys;
    private final BankTransactionRepository transactions;

    public IdempotencyService(IdempotencyKeyRepository keys, BankTransactionRepository transactions) {
        this.keys = keys;
        this.transactions = transactions;
    }

    public Result execute(UUID userId, String key, String requestHash, Function<UUID, TransactionResponse> action) {
        String k = validate(key);
        Optional<IdempotencyKey> existing = keys.findByUserIdAndKey(userId, k);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash);
        }
        try {
            return new Result(action.apply(UUID.randomUUID()), false);
        } catch (DataIntegrityViolationException e) {
            IdempotencyKey winner = keys.findByUserIdAndKey(userId, k).orElseThrow(() -> e);
            return replay(winner, requestHash);
        }
    }

    /** Dipanggil di dalam transaksi bisnis, sebelum uang dipindahkan. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void claim(UUID userId, String key, String requestHash, UUID transactionId) {
        keys.saveAndFlush(new IdempotencyKey(userId, validate(key), requestHash, transactionId));
    }

    private Result replay(IdempotencyKey stored, String requestHash) {
        if (!stored.getRequestHash().equals(requestHash)) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency-Key ini sudah dipakai untuk permintaan yang berbeda");
        }
        TransactionResponse response = transactions.findWithAccountsById(stored.getTransactionId())
                .map(TransactionResponse::of)
                .orElseThrow(() -> new IllegalStateException("Transaksi untuk kunci idempotensi tidak ditemukan"));
        return new Result(response, true);
    }

    private static String validate(String key) {
        if (key == null || key.isBlank()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Header Idempotency-Key wajib diisi");
        }
        String k = key.trim();
        if (k.length() < 8 || k.length() > 80 || !k.matches("[A-Za-z0-9_\\-]+")) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "Idempotency-Key harus 8-80 karakter huruf, angka, '-' atau '_' (contoh: UUID)");
        }
        return k;
    }

    public static String hash(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(String.join("|", parts).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Result(TransactionResponse transaction, boolean replayed) {}
}
