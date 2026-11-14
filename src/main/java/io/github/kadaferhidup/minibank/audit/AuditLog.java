package io.github.kadaferhidup.minibank.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false)
    private String action;

    private String target;

    @Column(name = "ip_address")
    private String ipAddress;

    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    AuditLog(UUID actorId, String action, String target, String ipAddress, String details) {
        this.actorId = actorId;
        this.action = action;
        this.target = target;
        this.ipAddress = ipAddress;
        this.details = details == null ? null : details.substring(0, Math.min(details.length(), 500));
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public String getIpAddress() { return ipAddress; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
