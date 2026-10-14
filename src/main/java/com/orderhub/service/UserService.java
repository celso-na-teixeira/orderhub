package com.orderhub.service;

import com.orderhub.domain.User;
import com.orderhub.dto.UpdateProfileRequest;
import com.orderhub.exception.ResourceNotFoundException;
import com.orderhub.exception.UnauthorizedException;
import com.orderhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    public User updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getById(userId);
        boolean changingEmail = request.email() != null && !request.email().isBlank();
        boolean changingPassword = request.newPassword() != null;

        // BR-71: changing the login email or password requires the current password
        if (changingEmail || changingPassword) {
            if (request.currentPassword() == null
                    || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                log.warn("Rejected profile update for user {}: current password missing or incorrect", userId);
                throw new UnauthorizedException("Current password is required to change your email or password");
            }
        }

        if (changingEmail) {
            user.setEmail(request.email());
        }
        if (changingPassword) {
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
            log.info("Password changed for user {}", userId);
        }
        return user;
    }
}
