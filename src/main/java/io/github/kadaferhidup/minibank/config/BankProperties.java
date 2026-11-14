package io.github.kadaferhidup.minibank.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Semua angka aturan bank dikumpulkan di sini supaya mudah diatur lewat application.yml. */
@Validated
@ConfigurationProperties(prefix = "minibank")
public record BankProperties(
        Jwt jwt,
        Limits limits,
        Pin pin,
        Login login,
        Admin admin,
        ZoneId zone) {

    public record Jwt(@NotBlank @Size(min = 32, message = "minimal 32 karakter") String secret, Duration ttl, String issuer) {}

    public record Limits(BigDecimal minTransfer, BigDecimal maxPerTransaction, BigDecimal dailyTransfer, int maxAccountsPerUser) {}

    public record Pin(int maxAttempts, Duration lockDuration) {}

    public record Login(int maxFailures, Duration window) {}

    public record Admin(String email, String password, String name) {}
}
