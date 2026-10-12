package com.orderhub.service;

import com.orderhub.domain.User;
import com.orderhub.dto.UpdateProfileRequest;
import com.orderhub.exception.ResourceNotFoundException;
import com.orderhub.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    public User updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getById(userId);
        if (request.email() != null && !request.email().isBlank()) {
            user.setEmail(request.email());
        }
        if (request.newPassword() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
            log.info("Password changed for user {}", userId);
        }
        return user;
    }
}
