package io.github.kadaferhidup.minibank.auth;

import io.github.kadaferhidup.minibank.account.Account;
import io.github.kadaferhidup.minibank.account.AccountResponse;
import io.github.kadaferhidup.minibank.account.AccountService;
import io.github.kadaferhidup.minibank.audit.AuditService;
import io.github.kadaferhidup.minibank.auth.AuthDtos.LoginRequest;
import io.github.kadaferhidup.minibank.auth.AuthDtos.ProfileResponse;
import io.github.kadaferhidup.minibank.auth.AuthDtos.RegisterRequest;
import io.github.kadaferhidup.minibank.auth.AuthDtos.RegisterResponse;
import io.github.kadaferhidup.minibank.common.ApiException;
import io.github.kadaferhidup.minibank.common.ErrorCode;
import io.github.kadaferhidup.minibank.user.Role;
import io.github.kadaferhidup.minibank.user.User;
import io.github.kadaferhidup.minibank.user.UserRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** Hash pembanding supaya waktu respons login sama, baik email terdaftar maupun tidak. */
    private final String dummyHash;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PinService pins;
    private final AccountService accounts;
    private final JwtService jwt;
    private final LoginAttemptGuard guard;
    private final AuditService audit;

    public AuthService(UserRepository users, PasswordEncoder encoder, PinService pins, AccountService accounts,
                       JwtService jwt, LoginAttemptGuard guard, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.pins = pins;
        this.accounts = accounts;
        this.jwt = jwt;
        this.guard = guard;
        this.audit = audit;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public RegisterResponse register(RegisterRequest req, String ip) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN, "Email sudah terdaftar");
        }
        String pinHash = pins.hash(req.pin());
        User user = users.save(User.create(req.fullName(), email, req.phone(), encoder.encode(req.password()),
                pinHash, Role.CUSTOMER));
        Account account = accounts.open(user);
        audit.log(user.getId(), "REGISTER", account.getAccountNumber(), ip, null);
        return new RegisterResponse(profile(user), AccountResponse.of(account), jwt.issue(user));
    }

    public JwtService.TokenResponse login(LoginRequest req, String ip) {
        String email = req.email().trim().toLowerCase();
        guard.attempt(email);
        User user = users.findByEmail(email).orElse(null);
        boolean ok = encoder.matches(req.password(), user != null ? user.getPasswordHash() : dummyHash) && user != null;
        if (!ok) {
            audit.log(user != null ? user.getId() : null, "LOGIN_FAILED", email, ip, null);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Email atau password salah");
        }
        guard.reset(email);
        audit.log(user.getId(), "LOGIN", email, ip, null);
        return jwt.issue(user);
    }

    @Transactional(readOnly = true)
    public ProfileResponse me(UUID userId) {
        return profile(users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS, "Pengguna tidak ditemukan")));
    }

    static ProfileResponse profile(User u) {
        return new ProfileResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole().name(),
                u.getCreatedAt());
    }
}
