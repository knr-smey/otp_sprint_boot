package com.example.spring_boot_project_api.mapper;

import com.example.spring_boot_project_api.dto.response.UserResponse;
import com.example.spring_boot_project_api.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

	public UserResponse toResponse(User user) {
		return UserResponse.builder()
				.id(user.getId())
				.username(user.getUsername())
				.twoFactorEnabled(user.isTwoFactorEnabled())
				.build();
	}
}
