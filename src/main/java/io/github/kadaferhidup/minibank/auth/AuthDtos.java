package io.github.kadaferhidup.minibank.auth;

import io.github.kadaferhidup.minibank.account.AccountResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 100) String fullName,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Pattern(regexp = "^(\\+62|0)8[0-9]{8,11}$", message = "format nomor HP tidak valid, contoh 081234567890") String phone,
            @NotBlank @Size(min = 8, max = 72, message = "password minimal 8 karakter")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "password harus berisi huruf dan angka") String password,
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "PIN harus 6 digit angka") String pin) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record ProfileResponse(UUID id, String fullName, String email, String phone, String role, Instant createdAt) {
    }

    public record RegisterResponse(ProfileResponse profile, AccountResponse account, JwtService.TokenResponse token) {
    }
}
