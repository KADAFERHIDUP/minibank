package io.github.kadaferhidup.minibank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kadaferhidup.minibank.common.ApiException;
import io.github.kadaferhidup.minibank.config.BankProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptGuardTest {

    private Instant now = Instant.parse("2026-11-21T08:00:00Z");

    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };

    private final LoginAttemptGuard guard = new LoginAttemptGuard(new BankProperties(null, null, null,
            new BankProperties.Login(3, Duration.ofMinutes(15)), null, ZoneOffset.UTC), clock);

    @Test
    void attemptsAreCountedBeforeThePasswordIsChecked() {
        guard.attempt("a@test.id");
        guard.attempt("a@test.id");
        guard.attempt("a@test.id");
        assertThatThrownBy(() -> guard.attempt("a@test.id")).isInstanceOf(ApiException.class);

        now = now.plus(Duration.ofMinutes(16));
        guard.attempt("a@test.id");
    }

    @Test
    void successfulLoginClearsTheCount() {
        guard.attempt("b@test.id");
        guard.attempt("b@test.id");
        guard.reset("b@test.id");
        guard.attempt("b@test.id");
        guard.attempt("b@test.id");
        guard.attempt("b@test.id");
    }

    @Test
    void randomEmailsDoNotGrowMemoryForever() {
        for (int i = 0; i < LoginAttemptGuard.MAX_TRACKED; i++) {
            guard.attempt("acak" + i + "@test.id");
        }
        now = now.plus(Duration.ofMinutes(16));
        guard.attempt("terakhir@test.id");
        assertThat(guard.tracked()).isLessThan(10);
    }
}
