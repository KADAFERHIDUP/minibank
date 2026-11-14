package io.github.kadaferhidup.minibank.account;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    @EntityGraph(attributePaths = "owner")
    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    List<Account> findByOwnerIdOrderByCreatedAt(UUID ownerId);

    long countByOwnerId(UUID ownerId);

    /** Hanya id, sengaja tidak memuat entitas supaya data saldo baru dibaca setelah baris dikunci. */
    @Query("select a.id from Account a where a.accountNumber = :number")
    Optional<UUID> findIdByAccountNumber(@Param("number") String number);

    @Query("select a.id from Account a where a.type = :type")
    List<UUID> findIdsByType(@Param("type") AccountType type);

    /**
     * Mengunci baris rekening (SELECT ... FOR UPDATE) selalu dalam urutan id yang sama.
     * Urutan yang konsisten mencegah deadlock saat A transfer ke B bersamaan dengan B transfer ke A.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id")
    List<Account> lockAllOrdered(@Param("ids") Collection<UUID> ids);
}
