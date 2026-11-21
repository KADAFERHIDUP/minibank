package io.github.kadaferhidup.minibank.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {

    /** Diisi RequestGuardFilter untuk setiap permintaan. */
    public static final String CLIENT_IP_ATTRIBUTE = "minibank.clientIp";

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public static boolean isAdmin(Jwt jwt) {
        return "ADMIN".equals(jwt.getClaimAsString("role"));
    }

    /** Alamat IP klien yang sudah ditentukan RequestGuardFilter (benar juga di balik reverse proxy). */
    public static String ip(HttpServletRequest request) {
        return request.getAttribute(CLIENT_IP_ATTRIBUTE) instanceof String ip ? ip : request.getRemoteAddr();
    }
}
