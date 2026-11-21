package io.github.kadaferhidup.minibank.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kadaferhidup.minibank.common.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Berjalan sebelum Spring Security: menentukan IP klien yang sebenarnya (dipakai juga oleh audit log)
 * dan membatasi laju permintaan per IP untuk login, daftar, dan cek nama rekening.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestGuardFilter extends OncePerRequestFilter {

    private static final Pattern INQUIRY = Pattern.compile("^/api/accounts/[^/]+/inquiry$");
    private static final Pattern IP_CHARS = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private final RateLimitProperties props;
    private final ObjectMapper json;
    private final RateLimiter login;
    private final RateLimiter register;
    private final RateLimiter inquiry;

    public RequestGuardFilter(RateLimitProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
        this.login = new RateLimiter(props.loginPerMinute(), Duration.ofMinutes(1));
        this.register = new RateLimiter(props.registerPerHour(), Duration.ofHours(1));
        this.inquiry = new RateLimiter(props.inquiryPerMinute(), Duration.ofMinutes(1));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = clientIp(request, props.trustForwardedFor());
        request.setAttribute(CurrentUser.CLIENT_IP_ATTRIBUTE, ip);
        RateLimiter limiter = props.enabled() ? limiterFor(request) : null;
        if (limiter != null && !limiter.tryAcquire(ip)) {
            tooMany(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private RateLimiter limiterFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("POST".equals(method) && "/api/auth/login".equals(path)) {
            return login;
        }
        if ("POST".equals(method) && "/api/auth/register".equals(path)) {
            return register;
        }
        if ("GET".equals(method) && INQUIRY.matcher(path).matches()) {
            return inquiry;
        }
        return null;
    }

    /**
     * Di belakang reverse proxy, alamat koneksi adalah alamat proxy. Proxy menambahkan IP klien di
     * akhir X-Forwarded-For; entri di depannya bisa dikarang klien, jadi hanya entri terakhir yang dipakai.
     */
    static String clientIp(HttpServletRequest request, boolean trustForwardedFor) {
        if (trustForwardedFor) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null) {
                int comma = xff.lastIndexOf(',');
                String last = (comma >= 0 ? xff.substring(comma + 1) : xff).trim();
                if (IP_CHARS.matcher(last).matches()) {
                    return last;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private void tooMany(HttpServletResponse response) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://minibank.dev/errors/too-many-attempts");
        body.put("title", "Too Many Requests");
        body.put("status", 429);
        body.put("detail", "Terlalu banyak permintaan dari alamat ini. Coba lagi sebentar lagi");
        body.put("code", "TOO_MANY_ATTEMPTS");
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), body);
    }
}
