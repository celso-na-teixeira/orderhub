package com.orderhub.mapper;

import com.orderhub.domain.User;
import com.orderhub.dto.UserResponse;
import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {

    UserResponse toResponse(User user);
}
