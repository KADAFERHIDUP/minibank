package io.github.kadaferhidup.minibank.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RateLimiterTest {

    private final AtomicLong now = new AtomicLong();

    @Test
    void bucketEmptiesThenRefillsOverTime() {
        RateLimiter limiter = new RateLimiter(3, Duration.ofMinutes(1), now::get);
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
        assertThat(limiter.tryAcquire("2.2.2.2")).as("IP lain punya jatah sendiri").isTrue();

        now.addAndGet(Duration.ofSeconds(20).toNanos());
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
    }

    @Test
    void manyDistinctAddressesDoNotGrowMemoryForever() {
        RateLimiter limiter = new RateLimiter(5, Duration.ofMinutes(1), now::get);
        for (int i = 0; i < RateLimiter.MAX_KEYS + 1_000; i++) {
            limiter.tryAcquire("ip-" + i);
        }
        assertThat(limiter.size()).isLessThanOrEqualTo(RateLimiter.MAX_KEYS);
    }

    @Test
    void onlyTheLastForwardedEntryIsTrusted() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.5");
        req.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.7");
        assertThat(RequestGuardFilter.clientIp(req, true)).isEqualTo("203.0.113.7");
        assertThat(RequestGuardFilter.clientIp(req, false)).isEqualTo("10.0.0.5");

        MockHttpServletRequest junk = new MockHttpServletRequest();
        junk.setRemoteAddr("10.0.0.5");
        junk.addHeader("X-Forwarded-For", "<script>");
        assertThat(RequestGuardFilter.clientIp(junk, true)).isEqualTo("10.0.0.5");
    }

    @Test
    void weakAdminPasswordStopsStartup() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> AdminSeeder.requireStrong("admin123"))
                .isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> AdminSeeder.requireStrong("passwordpanjang"))
                .isInstanceOf(IllegalStateException.class);
        AdminSeeder.requireStrong("Teller12345");
    }
}
