package io.github.kadaferhidup.minibank.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SecurityRulesTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void everyTableHasRowLevelSecurityForSupabase() {
        var open = jdbc.queryForList("""
                select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'public' and c.relkind = 'r' and not c.relrowsecurity
                  and c.relname <> 'flyway_schema_history'
                """, String.class);
        assertThat(open).isEmpty();
    }

    @Test
    void endpointsNeedToken() {
        assertThat(get("/api/accounts", null).status()).isEqualTo(401);
        assertThat(get("/api/accounts", "token.palsu.sekali").status()).isEqualTo(401);
    }

    @Test
    void customerCannotUseAdminEndpoints() {
        Customer c = newCustomer("Zaki Nasabah");
        assertThat(get("/api/admin/ledger/verify", c.token()).status()).isEqualTo(403);
        assertThat(postIdem("/api/admin/deposits", Map.of("accountNumber", c.account(), "amount", 1000000),
                c.token(), key()).status()).isEqualTo(403);
    }

    @Test
    void customerCannotSeeOrSpendFromOthersAccount() {
        Customer owner = newCustomer("Ani Pemilik");
        Customer thief = newCustomer("Beni Usil");
        deposit(owner.account(), "100000");

        assertThat(get("/api/accounts/" + owner.account(), thief.token()).status()).isEqualTo(404);
        assertThat(get("/api/accounts/" + owner.account() + "/statement", thief.token()).status()).isEqualTo(404);

        Resp steal = postIdem("/api/transfers", Map.of("fromAccount", owner.account(), "toAccount", thief.account(),
                "amount", 50000, "pin", PIN), thief.token(), key());
        assertThat(steal.status()).isEqualTo(404);
        assertThat(balance(owner)).isEqualByComparingTo("100000");
    }

    @Test
    void transferWithoutIdempotencyKeyIsRejected() {
        Customer c = newCustomer("Cici Lupa");
        Resp r = post("/api/transfers", Map.of("fromAccount", c.account(), "toAccount", c.account(),
                "amount", 10000, "pin", PIN), c.token());
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.code()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    void registrationValidatesInput() {
        Resp weakPin = post("/api/auth/register", Map.of("fullName", "Dedi Lemah", "email", "dedi@test.id",
                "phone", "081234567890", "password", PASSWORD, "pin", "123456"), null);
        assertThat(weakPin.code()).isEqualTo("WEAK_PIN");

        Resp bad = post("/api/auth/register", Map.of("fullName", "E", "email", "bukan-email",
                "phone", "12345", "password", "pendek", "pin", "12"), null);
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body().path("fields").fieldNames()).toIterable()
                .contains("fullName", "email", "phone", "password", "pin");

        Customer c = newCustomer("Fani Ganda");
        Resp dup = post("/api/auth/register", Map.of("fullName", "Fani Lagi", "email", c.email(),
                "phone", "081234567890", "password", PASSWORD, "pin", "246813"), null);
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.code()).isEqualTo("EMAIL_TAKEN");
    }

    @Test
    void unknownEndpointReturnsProblemJson() {
        Customer c = newCustomer("Indra Nyasar");
        Resp r = get("/api/tidak-ada", c.token());
        assertThat(r.status()).isEqualTo(404);
        assertThat(r.code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void webUiIsPublicButApiStaysLocked() {
        var page = http.getForEntity("/", String.class);
        assertThat(page.getStatusCode().value()).isEqualTo(200);
        assertThat(page.getBody()).contains("/js/app.js");
        assertThat(http.getForEntity("/js/app.js", String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(http.getForEntity("/css/app.css", String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/me", null).status()).isEqualTo(401);
    }

    @Test
    void repeatedFailedLoginIsThrottled() {
        Customer c = newCustomer("Gilang Lupa");
        for (int i = 0; i < 5; i++) {
            assertThat(post("/api/auth/login", Map.of("email", c.email(), "password", "SalahBanget1"), null).status())
                    .isEqualTo(401);
        }
        Resp blocked = post("/api/auth/login", Map.of("email", c.email(), "password", PASSWORD), null);
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.code()).isEqualTo("TOO_MANY_ATTEMPTS");
    }

    @Test
    void unknownEmailAndWrongPasswordLookTheSame() {
        Resp unknown = post("/api/auth/login", Map.of("email", "tidakada@test.id", "password", "Apa1234567"), null);
        Customer c = newCustomer("Hana Rahasia");
        Resp wrong = post("/api/auth/login", Map.of("email", c.email(), "password", "Salah123456"), null);
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(unknown.body().path("detail").asText()).isEqualTo(wrong.body().path("detail").asText());
    }
}
