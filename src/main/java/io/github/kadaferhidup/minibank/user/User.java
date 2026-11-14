package io.github.kadaferhidup.minibank.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "pin_hash")
    private String pinHash;

    @Column(name = "pin_failed_attempts", nullable = false)
    private int pinFailedAttempts;

    @Column(name = "pin_locked_until")
    private Instant pinLockedUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public static User create(String fullName, String email, String phone, String passwordHash, String pinHash, Role role) {
        User u = new User();
        u.id = UUID.randomUUID();
        u.fullName = fullName.trim();
        u.email = email.trim().toLowerCase();
        u.phone = phone.trim();
        u.passwordHash = passwordHash;
        u.pinHash = pinHash;
        u.role = role;
        u.createdAt = Instant.now();
        return u;
    }

    public boolean isPinLocked(Instant now) {
        return pinLockedUntil != null && pinLockedUntil.isAfter(now);
    }

    /** Mencatat PIN salah. Mengembalikan true kalau PIN sekarang terkunci. */
    public boolean registerPinFailure(int maxAttempts, Instant lockUntil) {
        pinFailedAttempts++;
        if (pinFailedAttempts >= maxAttempts) {
            pinFailedAttempts = 0;
            pinLockedUntil = lockUntil;
            return true;
        }
        return false;
    }

    public void resetPinFailures() {
        pinFailedAttempts = 0;
        pinLockedUntil = null;
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public String getPinHash() { return pinHash; }
    public int getPinFailedAttempts() { return pinFailedAttempts; }
    public Instant getPinLockedUntil() { return pinLockedUntil; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
