package io.github.kadaferhidup.minibank.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Batas permintaan per alamat IP untuk rute yang bisa disalahgunakan tanpa banyak biaya:
 * login (tebak password), daftar (akun palsu massal), dan cek nama pemilik rekening.
 *
 * @param trustForwardedFor ambil IP klien dari entri terakhir X-Forwarded-For, yaitu entri yang
 *                          ditulis reverse proxy platform. Matikan kalau aplikasi bisa diakses
 *                          langsung tanpa proxy, karena header itu bisa dikarang klien.
 */
@Validated
@ConfigurationProperties(prefix = "minibank.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        boolean trustForwardedFor,
        @Min(1) int loginPerMinute,
        @Min(1) int registerPerHour,
        @Min(1) int inquiryPerMinute) {
}
