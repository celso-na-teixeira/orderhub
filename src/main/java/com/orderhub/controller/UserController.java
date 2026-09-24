package com.orderhub.controller;

import com.orderhub.dto.UpdateProfileRequest;
import com.orderhub.dto.UserResponse;
import com.orderhub.mapper.UserMapper;
import com.orderhub.security.AppUserDetails;
import com.orderhub.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserDetails user) {
        return userMapper.toResponse(userService.getById(user.getId()));
    }

    @PatchMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal AppUserDetails user,
                                 @Valid @RequestBody UpdateProfileRequest request) {
        return userMapper.toResponse(userService.updateProfile(user.getId(), request));
    }
}
