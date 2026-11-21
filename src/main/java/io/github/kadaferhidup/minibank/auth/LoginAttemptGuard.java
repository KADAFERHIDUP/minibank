package io.github.kadaferhidup.minibank.auth;

import io.github.kadaferhidup.minibank.common.ApiException;
import io.github.kadaferhidup.minibank.common.ErrorCode;
import io.github.kadaferhidup.minibank.config.BankProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Membatasi percobaan login per email dalam jendela waktu tertentu untuk menahan brute force.
 *
 * <p>Setiap percobaan dicatat <em>sebelum</em> password dicek, dalam satu operasi atomik. Login yang
 * berhasil menghapus catatannya. Dengan begitu seratus tebakan yang dikirim bersamaan tetap hanya
 * mendapat jatah {@code max-failures}, bukan semuanya lolos karena membaca hitungan yang sama.
 *
 * <p>Disimpan di memori: cukup untuk satu instance. Untuk banyak instance, pindahkan ke Redis.
 */
@Component
public class LoginAttemptGuard {

    /** Batas jumlah email yang dilacak, supaya email acak tidak membuat memori terus tumbuh. */
    static final int MAX_TRACKED = 20_000;

    private final ConcurrentMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final BankProperties props;
    private final Clock clock;

    public LoginAttemptGuard(BankProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    /** Memesan satu percobaan login. Melempar TOO_MANY_ATTEMPTS kalau jatahnya sudah habis. */
    public void attempt(String email) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(props.login().window());
        int max = props.login().maxFailures();
        boolean[] blocked = {false};
        attempts.compute(email, (k, q) -> {
            Deque<Instant> d = q != null ? q : new ArrayDeque<>();
            prune(d, cutoff);
            if (d.size() >= max) {
                blocked[0] = true;
            } else {
                d.addLast(now);
            }
            return d;
        });
        if (attempts.size() > MAX_TRACKED) {
            sweep(cutoff);
        }
        if (blocked[0]) {
            throw new ApiException(ErrorCode.TOO_MANY_ATTEMPTS,
                    "Terlalu banyak percobaan login gagal. Coba lagi beberapa menit lagi");
        }
    }

    /** Dipanggil setelah login berhasil. */
    public void reset(String email) {
        attempts.remove(email);
    }

    int tracked() {
        return attempts.size();
    }

    private void sweep(Instant cutoff) {
        for (String key : attempts.keySet()) {
            attempts.computeIfPresent(key, (k, d) -> {
                prune(d, cutoff);
                return d.isEmpty() ? null : d;
            });
        }
    }

    private static void prune(Deque<Instant> d, Instant cutoff) {
        while (!d.isEmpty() && d.peekFirst().isBefore(cutoff)) {
            d.pollFirst();
        }
    }
}
