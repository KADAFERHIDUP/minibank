package io.github.kadaferhidup.minibank.auth;

import io.github.kadaferhidup.minibank.audit.AuditService;
import io.github.kadaferhidup.minibank.common.ApiException;
import io.github.kadaferhidup.minibank.common.ErrorCode;
import io.github.kadaferhidup.minibank.config.BankProperties;
import io.github.kadaferhidup.minibank.user.User;
import io.github.kadaferhidup.minibank.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifikasi PIN transaksi. Berjalan di transaksi database sendiri (REQUIRES_NEW) supaya hitungan
 * PIN salah tetap tersimpan walaupun permintaan transfernya ditolak.
 */
@Service
public class PinService {

    private static final Set<String> WEAK = Set.of("123456", "654321", "000000", "111111", "222222", "333333",
            "444444", "555555", "666666", "777777", "888888", "999999", "112233", "121212", "123123");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final BankProperties props;
    private final AuditService audit;
    private final Clock clock;

    public PinService(UserRepository users, PasswordEncoder encoder, BankProperties props, AuditService audit, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
        this.audit = audit;
        this.clock = clock;
    }

    public static void requireStrong(String pin) {
        if (pin == null || !pin.matches("\\d{6}")) {
            throw new ApiException(ErrorCode.WEAK_PIN, "PIN harus 6 digit angka");
        }
        if (WEAK.contains(pin)) {
            throw new ApiException(ErrorCode.WEAK_PIN, "PIN terlalu mudah ditebak, pilih kombinasi lain");
        }
    }

    public String hash(String pin) {
        requireStrong(pin);
        return encoder.encode(pin);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void verify(UUID userId, String pin, String ip) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS, "Pengguna tidak ditemukan"));
        Instant now = clock.instant();
        if (user.getPinHash() == null) {
            throw new ApiException(ErrorCode.PIN_NOT_SET, "Akun ini belum punya PIN transaksi");
        }
        if (user.isPinLocked(now)) {
            throw locked(user);
        }
        if (pin != null && encoder.matches(pin, user.getPinHash())) {
            if (user.getPinFailedAttempts() > 0 || user.getPinLockedUntil() != null) {
                user.resetPinFailures();
            }
            return;
        }
        int max = props.pin().maxAttempts();
        boolean nowLocked = user.registerPinFailure(max, now.plus(props.pin().lockDuration()));
        audit.log(userId, nowLocked ? "PIN_LOCKED" : "PIN_FAILED", null, ip, null);
        if (nowLocked) {
            throw locked(user);
        }
        int left = max - user.getPinFailedAttempts();
        throw new ApiException(ErrorCode.INVALID_PIN, "PIN salah, sisa percobaan " + left + " kali");
    }

    private ApiException locked(User user) {
        ZoneId zone = clock.getZone();
        return new ApiException(ErrorCode.PIN_LOCKED,
                "PIN terkunci karena salah berkali-kali. Coba lagi setelah pukul "
                        + TIME.format(user.getPinLockedUntil().atZone(zone)));
    }
}
