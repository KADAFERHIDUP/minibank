package io.github.kadaferhidup.minibank.config;

import io.github.kadaferhidup.minibank.audit.AuditService;
import io.github.kadaferhidup.minibank.user.Role;
import io.github.kadaferhidup.minibank.user.User;
import io.github.kadaferhidup.minibank.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Membuat akun admin (teller) pertama dari environment variable, kalau belum ada. */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final BankProperties props;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AdminSeeder(BankProperties props, UserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        BankProperties.Admin admin = props.admin();
        if (admin == null || !StringUtils.hasText(admin.email()) || !StringUtils.hasText(admin.password())) {
            return;
        }
        String email = admin.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            return;
        }
        User user = User.create(StringUtils.hasText(admin.name()) ? admin.name() : "Teller MiniBank",
                email, "081200000000", encoder.encode(admin.password()), null, Role.ADMIN);
        users.save(user);
        audit.log(user.getId(), "ADMIN_SEEDED", email, null, null);
        log.info("Akun admin {} dibuat", email);
    }
}
