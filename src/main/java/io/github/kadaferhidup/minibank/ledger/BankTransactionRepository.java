package io.github.kadaferhidup.minibank.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID> {

    @EntityGraph(attributePaths = {"sourceAccount", "destAccount", "sourceAccount.owner", "destAccount.owner"})
    Optional<BankTransaction> findByReference(String reference);

    @EntityGraph(attributePaths = {"sourceAccount", "destAccount"})
    Optional<BankTransaction> findWithAccountsById(UUID id);

    @Query("""
            select coalesce(sum(t.amount), 0) from BankTransaction t
            where t.sourceAccount.id = :accountId and t.type = :type and t.createdAt >= :since
            """)
    BigDecimal sumOutgoingSince(@Param("accountId") UUID accountId,
                                @Param("type") TransactionType type,
                                @Param("since") Instant since);
}
