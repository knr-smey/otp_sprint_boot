package com.example.spring_boot_project_api.exception;

public class DuplicateUsernameException extends RuntimeException {

	public DuplicateUsernameException(String username) {
		super("Username '" + username + "' is already taken.");
	}
}
