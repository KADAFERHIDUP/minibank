package io.github.kadaferhidup.minibank.ledger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    @Query(value = """
            select e from LedgerEntry e
              join fetch e.transaction t
              left join fetch t.sourceAccount
              left join fetch t.destAccount
            where e.account.id = :accountId and e.createdAt >= :from and e.createdAt < :to
            order by e.id desc
            """,
            countQuery = """
            select count(e) from LedgerEntry e
            where e.account.id = :accountId and e.createdAt >= :from and e.createdAt < :to
            """)
    Page<LedgerEntry> statement(@Param("accountId") UUID accountId,
                                @Param("from") Instant from,
                                @Param("to") Instant to,
                                Pageable pageable);

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    /** Total debit dan total kredit seluruh buku besar. Harus selalu sama. */
    @Query(value = """
            select coalesce(sum(case when direction = 'DEBIT' then amount else 0 end), 0) as debit,
                   coalesce(sum(case when direction = 'CREDIT' then amount else 0 end), 0) as credit
            from ledger_entries
            """, nativeQuery = true)
    List<Object[]> totals();

    /** Rekening yang saldonya tidak sama dengan hasil hitung ulang dari buku besar. */
    @Query(value = """
            select a.account_number, a.balance,
                   coalesce(sum(case when e.direction = 'CREDIT' then e.amount else -e.amount end), 0) as ledger
            from accounts a
            left join ledger_entries e on e.account_id = a.id
            group by a.id, a.account_number, a.balance
            having a.balance <> coalesce(sum(case when e.direction = 'CREDIT' then e.amount else -e.amount end), 0)
            """, nativeQuery = true)
    List<Object[]> mismatchedAccounts();

    /** Transaksi yang debit dan kreditnya tidak seimbang. */
    @Query(value = """
            select t.reference from transactions t join ledger_entries e on e.transaction_id = t.id
            group by t.id, t.reference
            having sum(case when e.direction = 'DEBIT' then e.amount else 0 end)
                <> sum(case when e.direction = 'CREDIT' then e.amount else 0 end)
            """, nativeQuery = true)
    List<String> unbalancedTransactions();
}
