package io.github.kadaferhidup.minibank.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Basis test integrasi: aplikasi jalan penuh di port acak dengan PostgreSQL asli di Docker (Testcontainers).
 * Kontainer dibuat sekali dan dipakai bersama semua kelas test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTest {

    static final String PIN = "246810";
    static final String PASSWORD = "Rahasia123";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected TestRestTemplate http;

    @Autowired
    protected ObjectMapper json;

    private String adminToken;

    record Resp(int status, JsonNode body, HttpHeaders headers) {
        String code() {
            return body.path("code").asText();
        }
    }

    record Customer(String email, String token, String account) {
    }

    protected Resp call(HttpMethod method, String path, Object body, String token, Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        extraHeaders.forEach(headers::set);
        ResponseEntity<String> res = http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
        try {
            JsonNode node = res.getBody() == null ? json.createObjectNode() : json.readTree(res.getBody());
            return new Resp(res.getStatusCode().value(), node, res.getHeaders());
        } catch (Exception e) {
            throw new IllegalStateException("Respons bukan JSON: " + res.getBody(), e);
        }
    }

    protected Resp get(String path, String token) {
        return call(HttpMethod.GET, path, null, token, Map.of());
    }

    protected Resp post(String path, Object body, String token) {
        return call(HttpMethod.POST, path, body, token, Map.of());
    }

    protected Resp postIdem(String path, Object body, String token, String key) {
        return call(HttpMethod.POST, path, body, token, Map.of("Idempotency-Key", key));
    }

    protected Customer newCustomer(String name) {
        String email = name.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID().toString().substring(0, 8) + "@test.id";
        Resp r = post("/api/auth/register", Map.of(
                "fullName", name, "email", email, "phone", "081234567890",
                "password", PASSWORD, "pin", PIN), null);
        assertThat(r.status()).as(r.body().toString()).isEqualTo(201);
        return new Customer(email, r.body().at("/token/accessToken").asText(), r.body().at("/account/accountNumber").asText());
    }

    protected String adminToken() {
        if (adminToken == null) {
            Resp r = post("/api/auth/login", Map.of("email", "teller@minibank.test", "password", "Teller12345"), null);
            assertThat(r.status()).as(r.body().toString()).isEqualTo(200);
            adminToken = r.body().path("accessToken").asText();
        }
        return adminToken;
    }

    protected Resp deposit(String account, String amount) {
        Resp r = postIdem("/api/admin/deposits", Map.of("accountNumber", account, "amount", new BigDecimal(amount)),
                adminToken(), key());
        assertThat(r.status()).as(r.body().toString()).isEqualTo(201);
        return r;
    }

    protected Resp transfer(Customer from, String key, String to, String amount, String pin) {
        return postIdem("/api/transfers", Map.of(
                "fromAccount", from.account(), "toAccount", to,
                "amount", new BigDecimal(amount), "description", "Test transfer", "pin", pin), from.token(), key);
    }

    protected BigDecimal balance(Customer c) {
        Resp r = get("/api/accounts/" + c.account(), c.token());
        assertThat(r.status()).isEqualTo(200);
        return r.body().path("balance").decimalValue();
    }

    protected static String key() {
        return UUID.randomUUID().toString();
    }
}
