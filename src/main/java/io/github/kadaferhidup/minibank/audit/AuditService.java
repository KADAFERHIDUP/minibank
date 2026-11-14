package io.github.kadaferhidup.minibank.audit;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Jejak audit. Dipanggil di dalam transaksi bisnis supaya ikut tersimpan atau ikut batal,
 * dan di luar transaksi (misalnya login gagal) supaya tetap tercatat walau permintaannya ditolak.
 */
@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(UUID actorId, String action, String target, String ip, String details) {
        repo.save(new AuditLog(actorId, action, target, ip, details));
    }
}
