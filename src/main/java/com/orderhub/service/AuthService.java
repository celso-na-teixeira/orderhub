package com.orderhub.service;

import com.orderhub.domain.Customer;
import com.orderhub.domain.Role;
import com.orderhub.domain.User;
import com.orderhub.dto.AuthResponse;
import com.orderhub.dto.LoginRequest;
import com.orderhub.dto.RegisterRequest;
import com.orderhub.exception.ConflictException;
import com.orderhub.exception.ResourceNotFoundException;
import com.orderhub.exception.UnauthorizedException;
import com.orderhub.repository.CustomerRepository;
import com.orderhub.repository.UserRepository;
import com.orderhub.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user: {}", request);
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role() != null ? request.role() : Role.CUSTOMER);
        user.setEnabled(true);
        userRepository.save(user);

        customerRepository.save(new Customer(user, request.name(), request.email()));

        return AuthResponse.bearer(jwtService.generateToken(user), user.getEmail(), user.getRole().name());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("No account found for " + request.email()));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Failed login for {}", request.email());
            throw new UnauthorizedException("Invalid password");
        }

        log.info("User {} logged in", user.getEmail());
        return AuthResponse.bearer(jwtService.generateToken(user), user.getEmail(), user.getRole().name());
    }
}
