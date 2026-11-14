package io.github.kadaferhidup.minibank.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kadaferhidup.minibank.account.AccountNumberGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BankingFlowTest extends IntegrationTest {

    @Test
    void registerOpensAccountAndLoginWorks() {
        Customer c = newCustomer("Ahmad Umraithi");
        assertThat(AccountNumberGenerator.isValid(c.account())).isTrue();
        assertThat(balance(c)).isEqualByComparingTo("0");

        Resp login = post("/api/auth/login", Map.of("email", c.email(), "password", PASSWORD), null);
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.body().path("tokenType").asText()).isEqualTo("Bearer");

        Resp me = get("/api/me", login.body().path("accessToken").asText());
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.body().path("fullName").asText()).isEqualTo("Ahmad Umraithi");
        assertThat(me.body().has("passwordHash")).isFalse();
    }

    @Test
    void depositAndTransferMoveMoneyAndShowInStatement() {
        Customer sender = newCustomer("Budi Santoso");
        Customer receiver = newCustomer("Citra Lestari");
        deposit(sender.account(), "500000");

        Resp inquiry = get("/api/accounts/" + receiver.account() + "/inquiry", sender.token());
        assertThat(inquiry.status()).isEqualTo(200);
        assertThat(inquiry.body().path("holderName").asText()).isEqualTo("Ci*** Le*****");

        Resp t = transfer(sender, key(), receiver.account(), "125000", PIN);
        assertThat(t.status()).as(t.body().toString()).isEqualTo(201);
        assertThat(t.headers().getFirst("Idempotent-Replayed")).isEqualTo("false");
        String ref = t.body().path("reference").asText();
        assertThat(ref).startsWith("TRX");

        assertThat(balance(sender)).isEqualByComparingTo("375000");
        assertThat(balance(receiver)).isEqualByComparingTo("125000");

        Resp statement = get("/api/accounts/" + sender.account() + "/statement", sender.token());
        assertThat(statement.status()).isEqualTo(200);
        JsonNode lines = statement.body().path("items");
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).path("reference").asText()).isEqualTo(ref);
        assertThat(lines.get(0).path("direction").asText()).isEqualTo("DEBIT");
        assertThat(lines.get(0).path("counterparty").asText()).isEqualTo(receiver.account());
        assertThat(lines.get(0).path("balanceAfter").decimalValue()).isEqualByComparingTo("375000");
        assertThat(lines.get(1).path("type").asText()).isEqualTo("DEPOSIT");
        assertThat(lines.get(1).path("counterparty").asText()).isEqualTo("TUNAI");

        assertThat(get("/api/transactions/" + ref, receiver.token()).status()).isEqualTo(200);
        Customer stranger = newCustomer("Dodi Orang Lain");
        assertThat(get("/api/transactions/" + ref, stranger.token()).status()).isEqualTo(404);
    }

    @Test
    void withdrawalByTellerReducesBalance() {
        Customer c = newCustomer("Eka Putri");
        deposit(c.account(), "300000");
        Resp w = postIdem("/api/admin/withdrawals", Map.of("accountNumber", c.account(), "amount", 100000),
                adminToken(), key());
        assertThat(w.status()).as(w.body().toString()).isEqualTo(201);
        assertThat(w.body().path("type").asText()).isEqualTo("WITHDRAWAL");
        assertThat(balance(c)).isEqualByComparingTo("200000");

        Resp tooMuch = postIdem("/api/admin/withdrawals", Map.of("accountNumber", c.account(), "amount", 999999),
                adminToken(), key());
        assertThat(tooMuch.status()).isEqualTo(422);
        assertThat(tooMuch.code()).isEqualTo("INSUFFICIENT_BALANCE");
    }

    @Test
    void insufficientBalanceIsRejectedAndNothingIsWritten() {
        Customer sender = newCustomer("Fajar Nugroho");
        Customer receiver = newCustomer("Gita Permata");
        deposit(sender.account(), "50000");
        String k = key();

        Resp t = transfer(sender, k, receiver.account(), "60000", PIN);
        assertThat(t.status()).isEqualTo(422);
        assertThat(t.code()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(balance(sender)).isEqualByComparingTo("50000");
        assertThat(balance(receiver)).isEqualByComparingTo("0");

        // transaksi gagal tidak menyimpan kunci, jadi kunci yang sama boleh dipakai untuk percobaan baru
        Resp retry = transfer(sender, k, receiver.account(), "40000", PIN);
        assertThat(retry.status()).as(retry.body().toString()).isEqualTo(201);
        assertThat(balance(sender)).isEqualByComparingTo("10000");
    }

    @Test
    void sameIdempotencyKeyIsReplayedNotChargedTwice() {
        Customer sender = newCustomer("Hadi Wijaya");
        Customer receiver = newCustomer("Indah Sari");
        deposit(sender.account(), "200000");
        String k = key();

        Resp first = transfer(sender, k, receiver.account(), "50000", PIN);
        Resp second = transfer(sender, k, receiver.account(), "50000", PIN);
        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.headers().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(second.body().path("reference").asText()).isEqualTo(first.body().path("reference").asText());
        assertThat(balance(sender)).isEqualByComparingTo("150000");

        Resp different = transfer(sender, k, receiver.account(), "70000", PIN);
        assertThat(different.status()).isEqualTo(422);
        assertThat(different.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(balance(sender)).isEqualByComparingTo("150000");
    }

    @Test
    void transferRulesAreEnforced() {
        Customer sender = newCustomer("Joko Susilo");
        Customer receiver = newCustomer("Kartika Dewi");
        deposit(sender.account(), "200000000");

        assertThat(transfer(sender, key(), receiver.account(), "5000", PIN).code()).isEqualTo("INVALID_AMOUNT");
        assertThat(transfer(sender, key(), receiver.account(), "10000.555", PIN).code()).isEqualTo("INVALID_AMOUNT");
        assertThat(transfer(sender, key(), sender.account(), "10000", PIN).code()).isEqualTo("SAME_ACCOUNT");
        assertThat(transfer(sender, key(), receiver.account(), "60000000", PIN).code()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(transfer(sender, key(), "1000000001", "10000", PIN).status()).isEqualTo(422);

        // limit harian 100 juta: 50 + 50 boleh, berikutnya ditolak
        assertThat(transfer(sender, key(), receiver.account(), "50000000", PIN).status()).isEqualTo(201);
        assertThat(transfer(sender, key(), receiver.account(), "50000000", PIN).status()).isEqualTo(201);
        Resp over = transfer(sender, key(), receiver.account(), "10000", PIN);
        assertThat(over.code()).isEqualTo("DAILY_LIMIT_EXCEEDED");
        assertThat(balance(sender)).isEqualByComparingTo("100000000");
    }

    @Test
    void wrongPinLocksAfterThreeAttempts() {
        Customer sender = newCustomer("Lukman Hakim");
        Customer receiver = newCustomer("Maya Anggraini");
        deposit(sender.account(), "100000");

        Resp w1 = transfer(sender, key(), receiver.account(), "10000", "135790");
        assertThat(w1.code()).isEqualTo("INVALID_PIN");
        assertThat(w1.body().path("detail").asText()).contains("sisa percobaan 2");
        assertThat(transfer(sender, key(), receiver.account(), "10000", "135790").code()).isEqualTo("INVALID_PIN");
        Resp locked = transfer(sender, key(), receiver.account(), "10000", "135790");
        assertThat(locked.status()).isEqualTo(423);
        assertThat(locked.code()).isEqualTo("PIN_LOCKED");

        // PIN benar pun ditolak selama masih terkunci
        assertThat(transfer(sender, key(), receiver.account(), "10000", PIN).code()).isEqualTo("PIN_LOCKED");
        assertThat(balance(sender)).isEqualByComparingTo("100000");
    }

    @Test
    void frozenAccountCannotSendUntilUnfrozen() {
        Customer sender = newCustomer("Nina Kurnia");
        Customer receiver = newCustomer("Oki Pratama");
        deposit(sender.account(), "100000");

        Resp freeze = post("/api/admin/accounts/" + sender.account() + "/freeze", Map.of("reason", "Laporan penipuan"), adminToken());
        assertThat(freeze.status()).isEqualTo(200);
        assertThat(freeze.body().path("status").asText()).isEqualTo("FROZEN");

        assertThat(transfer(sender, key(), receiver.account(), "10000", PIN).code()).isEqualTo("ACCOUNT_NOT_ACTIVE");
        Resp toFrozen = transfer(receiver, key(), sender.account(), "10000", PIN);
        assertThat(toFrozen.status()).isEqualTo(422);

        assertThat(post("/api/admin/accounts/" + sender.account() + "/unfreeze", Map.of("reason", "Sudah diverifikasi"),
                adminToken()).status()).isEqualTo(200);
        assertThat(transfer(sender, key(), receiver.account(), "10000", PIN).status()).isEqualTo(201);

        Resp audit = get("/api/admin/audit-logs?size=100", adminToken());
        assertThat(audit.body().path("items").findValuesAsText("action")).contains("ACCOUNT_FROZEN", "ACCOUNT_UNFROZEN");
    }

    @Test
    void ledgerStaysBalanced() {
        Customer a = newCustomer("Putu Ayu");
        Customer b = newCustomer("Rudi Hartono");
        deposit(a.account(), "1000000");
        transfer(a, key(), b.account(), "250000", PIN);

        Resp report = get("/api/admin/ledger/verify", adminToken());
        assertThat(report.status()).isEqualTo(200);
        assertThat(report.body().path("balanced").asBoolean()).as(report.body().toString()).isTrue();
        BigDecimal debit = report.body().path("totalDebit").decimalValue();
        assertThat(debit).isEqualByComparingTo(report.body().path("totalCredit").decimalValue());
    }
}
