package io.github.kadaferhidup.minibank.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Skenario yang membedakan sistem bank dengan CRUD biasa: banyak permintaan uang di detik yang sama. */
class ConcurrencyTest extends IntegrationTest {

    @Test
    void parallelTransfersNeverOverdraw() throws Exception {
        Customer sender = newCustomer("Sari Paralel");
        Customer receiver = newCustomer("Tono Penerima");
        deposit(sender.account(), "100000");

        // 20 transfer @10.000 ditembak bersamaan dari saldo 100.000: tepat 10 berhasil, sisanya ditolak
        List<Resp> results = runTogether(20, i -> () -> transfer(sender, key(), receiver.account(), "10000", PIN));

        long ok = results.stream().filter(r -> r.status() == 201).count();
        long rejected = results.stream().filter(r -> "INSUFFICIENT_BALANCE".equals(r.code())).count();
        assertThat(ok).isEqualTo(10);
        assertThat(rejected).isEqualTo(10);
        assertThat(balance(sender)).isEqualByComparingTo("0");
        assertThat(balance(receiver)).isEqualByComparingTo("100000");
        assertThat(get("/api/admin/ledger/verify", adminToken()).body().path("balanced").asBoolean()).isTrue();
    }

    @Test
    void duplicateRequestsWithSameKeyCreateOneTransaction() throws Exception {
        Customer sender = newCustomer("Umar Kembar");
        Customer receiver = newCustomer("Vina Penerima");
        deposit(sender.account(), "100000");
        String sameKey = key();

        // tombol "Kirim" yang kepencet 10 kali sekaligus
        List<Resp> results = runTogether(10, i -> () -> transfer(sender, sameKey, receiver.account(), "25000", PIN));

        assertThat(results).allSatisfy(r -> assertThat(r.status()).as(r.body().toString()).isIn(200, 201));
        assertThat(results.stream().filter(r -> r.status() == 201).count()).isEqualTo(1);
        Set<String> refs = results.stream().map(r -> r.body().path("reference").asText()).collect(Collectors.toSet());
        assertThat(refs).hasSize(1);
        assertThat(balance(sender)).isEqualByComparingTo("75000");
        assertThat(balance(receiver)).isEqualByComparingTo("25000");
    }

    @Test
    void opposingTransfersDoNotDeadlock() throws Exception {
        Customer a = newCustomer("Wawan Silang");
        Customer b = newCustomer("Yuni Silang");
        deposit(a.account(), "1000000");
        deposit(b.account(), "1000000");

        // A ke B dan B ke A bersamaan: tanpa urutan kunci yang konsisten, ini rawan deadlock
        List<Resp> results = runTogether(20, i -> () -> i % 2 == 0
                ? transfer(a, key(), b.account(), "10000", PIN)
                : transfer(b, key(), a.account(), "10000", PIN));

        assertThat(results).allSatisfy(r -> assertThat(r.status()).as(r.body().toString()).isEqualTo(201));
        assertThat(balance(a)).isEqualByComparingTo("1000000");
        assertThat(balance(b)).isEqualByComparingTo("1000000");
    }

    private List<Resp> runTogether(int n, java.util.function.IntFunction<Callable<Resp>> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Resp>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Callable<Resp> c = task.apply(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    return c.call();
                }));
            }
            start.countDown();
            List<Resp> out = new ArrayList<>();
            for (Future<Resp> f : futures) {
                out.add(f.get());
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }
}
