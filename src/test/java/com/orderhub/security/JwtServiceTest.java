package com.orderhub.security;

import com.orderhub.domain.Role;
import com.orderhub.domain.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-long-enough-0123456789";

    @Test
    void generatedTokenCanBeParsed() {
        JwtService jwtService = new JwtService(SECRET, 60);

        String token = jwtService.generateToken(user());

        assertThat(jwtService.extractUsername(token)).isEqualTo("alice@example.com");
        assertThat(jwtService.parseClaims(token).get("role", String.class)).isEqualTo("CUSTOMER");
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        String token = new JwtService("another-secret-key-that-is-long-enough-9876543210", 60).generateToken(user());
        JwtService jwtService = new JwtService(SECRET, 60);

        assertThatThrownBy(() -> jwtService.parseClaims(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwtService = new JwtService(SECRET, -1);
        String token = jwtService.generateToken(user());

        assertThatThrownBy(() -> jwtService.parseClaims(token)).isInstanceOf(JwtException.class);
    }

    private static User user() {
        User user = new User();
        user.setId(3L);
        user.setEmail("alice@example.com");
        user.setRole(Role.CUSTOMER);
        return user;
    }
}
