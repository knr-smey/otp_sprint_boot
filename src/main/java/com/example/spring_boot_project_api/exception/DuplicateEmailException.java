package com.example.spring_boot_project_api.exception;

public class DuplicateEmailException extends RuntimeException {

	public DuplicateEmailException(String email) {
		super("Email '" + email + "' is already registered.");
	}
}
