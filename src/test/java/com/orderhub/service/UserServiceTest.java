package com.orderhub.service;

import com.orderhub.domain.User;
import com.orderhub.dto.UpdateProfileRequest;
import com.orderhub.exception.UnauthorizedException;
import com.orderhub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setEmail("alice@example.com");
        user.setPasswordHash("hashed-old-password");
        return user;
    }

    @Test
    void changingEmailWithCorrectCurrentPasswordSucceeds() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed-old-password")).thenReturn(true);

        User updated = userService.updateProfile(1L, new UpdateProfileRequest("new@example.com", "correct", null));

        assertThat(updated.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void changingPasswordWithCorrectCurrentPasswordSucceeds() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed-old-password")).thenReturn(true);
        when(passwordEncoder.encode("newSecret1")).thenReturn("hashed-new-password");

        userService.updateProfile(1L, new UpdateProfileRequest(null, "correct", "newSecret1"));

        assertThat(user.getPasswordHash()).isEqualTo("hashed-new-password");
    }

    @Test
    void changingEmailWithWrongCurrentPasswordIsRejected() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed-old-password")).thenReturn(false);

        assertThatThrownBy(() -> userService.updateProfile(1L, new UpdateProfileRequest("new@example.com", "wrong", null)))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void changingPasswordWithoutCurrentPasswordIsRejected() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateProfile(1L, new UpdateProfileRequest(null, null, "newSecret1")))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(user.getPasswordHash()).isEqualTo("hashed-old-password");
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void requestWithNoChangesDoesNotRequireCurrentPassword() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.updateProfile(1L, new UpdateProfileRequest(null, null, null));

        assertThat(result.getEmail()).isEqualTo("alice@example.com");
    }
}
