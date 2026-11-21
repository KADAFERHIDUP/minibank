package io.github.kadaferhidup.minibank.config;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/** Token bucket per kunci (biasanya alamat IP). Thread-safe, dan jumlah kunci yang disimpan dibatasi. */
final class RateLimiter {

    static final int MAX_KEYS = 50_000;

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final double capacity;
    private final double perNano;
    private final LongSupplier nanoTime;

    RateLimiter(int capacity, Duration period, LongSupplier nanoTime) {
        this.capacity = capacity;
        this.perNano = capacity / (double) period.toNanos();
        this.nanoTime = nanoTime;
    }

    RateLimiter(int capacity, Duration period) {
        this(capacity, period, System::nanoTime);
    }

    /** true kalau permintaan boleh lewat. */
    boolean tryAcquire(String key) {
        if (buckets.size() >= MAX_KEYS) {
            sweep();
        }
        boolean[] ok = {false};
        long now = nanoTime.getAsLong();
        buckets.compute(key, (k, b) -> {
            Bucket bucket = b != null ? b : new Bucket(capacity, now);
            bucket.refill(now, capacity, perNano);
            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                ok[0] = true;
            }
            return bucket;
        });
        return ok[0];
    }

    int size() {
        return buckets.size();
    }

    /** Membuang bucket yang sudah penuh lagi: pemiliknya sudah lama tidak mengirim apa-apa. */
    private void sweep() {
        long now = nanoTime.getAsLong();
        for (String key : buckets.keySet()) {
            buckets.computeIfPresent(key, (k, b) -> {
                b.refill(now, capacity, perNano);
                return b.tokens >= capacity ? null : b;
            });
        }
        if (buckets.size() >= MAX_KEYS) {
            // sangat banyak IP berbeda sekaligus: kosongkan daripada memori habis. Batas per email
            // dan kunci PIN tetap berlaku, jadi perlindungan utama tidak ikut hilang.
            buckets.clear();
        }
    }

    private static final class Bucket {
        double tokens;
        long last;

        Bucket(double tokens, long last) {
            this.tokens = tokens;
            this.last = last;
        }

        void refill(long now, double capacity, double perNano) {
            long elapsed = Math.max(0, now - last);
            tokens = Math.min(capacity, tokens + elapsed * perNano);
            last = now;
        }
    }
}
