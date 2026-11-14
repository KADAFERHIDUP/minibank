package io.github.kadaferhidup.minibank.auth;

import io.github.kadaferhidup.minibank.common.ApiException;
import io.github.kadaferhidup.minibank.common.ErrorCode;
import io.github.kadaferhidup.minibank.config.BankProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Membatasi login gagal per email dalam jendela waktu tertentu untuk menahan brute force.
 * Disimpan di memori: cukup untuk satu instance. Untuk banyak instance, pindahkan ke Redis.
 */
@Component
public class LoginAttemptGuard {

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final BankProperties props;
    private final Clock clock;

    public LoginAttemptGuard(BankProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    public void check(String email) {
        Deque<Instant> q = failures.get(email);
        if (q == null) {
            return;
        }
        synchronized (q) {
            prune(q);
            if (q.size() >= props.login().maxFailures()) {
                throw new ApiException(ErrorCode.TOO_MANY_ATTEMPTS,
                        "Terlalu banyak percobaan login gagal. Coba lagi beberapa menit lagi");
            }
        }
    }

    public void recordFailure(String email) {
        Deque<Instant> q = failures.computeIfAbsent(email, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q);
            q.addLast(clock.instant());
        }
    }

    public void reset(String email) {
        failures.remove(email);
    }

    private void prune(Deque<Instant> q) {
        Instant cutoff = clock.instant().minus(props.login().window());
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) {
            q.pollFirst();
        }
    }
}
