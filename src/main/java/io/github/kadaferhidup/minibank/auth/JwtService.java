package io.github.kadaferhidup.minibank.auth;

import io.github.kadaferhidup.minibank.config.BankProperties;
import io.github.kadaferhidup.minibank.user.User;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final BankProperties props;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, BankProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    public TokenResponse issue(User user) {
        Instant now = clock.instant();
        Instant exp = now.plus(props.jwt().ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(exp)
                .claim("role", user.getRole().name())
                .claim("name", user.getFullName())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", props.jwt().ttl().toSeconds(), exp);
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt) {}
}
