package io.github.kadaferhidup.minibank.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public static boolean isAdmin(Jwt jwt) {
        return "ADMIN".equals(jwt.getClaimAsString("role"));
    }

    /** Alamat IP klien. Di balik reverse proxy, atur server.forward-headers-strategy supaya nilainya benar. */
    public static String ip(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
