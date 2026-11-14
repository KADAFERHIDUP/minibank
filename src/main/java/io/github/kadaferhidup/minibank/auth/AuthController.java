package io.github.kadaferhidup.minibank.auth;

import io.github.kadaferhidup.minibank.auth.AuthDtos.LoginRequest;
import io.github.kadaferhidup.minibank.auth.AuthDtos.ProfileResponse;
import io.github.kadaferhidup.minibank.auth.AuthDtos.RegisterRequest;
import io.github.kadaferhidup.minibank.auth.AuthDtos.RegisterResponse;
import io.github.kadaferhidup.minibank.common.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Autentikasi")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Daftar nasabah baru dan langsung dibukakan rekening tabungan")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        return auth.register(req, CurrentUser.ip(http));
    }

    @PostMapping("/auth/login")
    @SecurityRequirements
    @Operation(summary = "Login dengan email dan password, mendapat access token JWT")
    public JwtService.TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return auth.login(req, CurrentUser.ip(http));
    }

    @GetMapping("/me")
    @Operation(summary = "Profil pengguna yang sedang login")
    public ProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(CurrentUser.id(jwt));
    }
}
