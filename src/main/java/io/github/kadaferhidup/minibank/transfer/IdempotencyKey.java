package io.github.kadaferhidup.minibank.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "idem_key", nullable = false)
    private String key;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyKey() {
    }

    IdempotencyKey(UUID userId, String key, String requestHash, UUID transactionId) {
        this.userId = userId;
        this.key = key;
        this.requestHash = requestHash;
        this.transactionId = transactionId;
        this.createdAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public String getKey() { return key; }
    public String getRequestHash() { return requestHash; }
    public UUID getTransactionId() { return transactionId; }
}
