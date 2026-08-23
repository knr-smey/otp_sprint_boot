package com.example.spring_boot_project_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringBootProjectApiApplication {

	public static void main(String[] args) {
		System.out.println("User A is developing the Spring Boot Project API");
		SpringApplication.run(SpringBootProjectApiApplication.class, args);
		System.out.println("Hello");
	}

}
