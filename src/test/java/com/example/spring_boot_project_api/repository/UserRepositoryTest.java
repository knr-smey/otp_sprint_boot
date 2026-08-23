package com.example.spring_boot_project_api.repository;

import com.example.spring_boot_project_api.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private jakarta.persistence.EntityManager entityManager;

	private User persistUser(String username) {
		User user = userRepository.save(User.builder()
				.username(username)
				.email(username + "@test.local")
				.passwordHash("hash")
				.build());
		entityManager.flush();
		return user;
	}

	@Test
	void save_persistsUser_withEmail() {
		User saved = persistUser("alice");

		assertThat(saved.getId()).isNotNull();
		Optional<User> found = userRepository.findByUsername("alice");
		assertThat(found).isPresent();
		assertThat(found.get().getEmail()).isEqualTo("alice@test.local");
	}

	@Test
	void findByUsername_returnsEmpty_forUnknownUser() {
		persistUser("bob");

		assertThat(userRepository.findByUsername("nobody")).isEmpty();
	}

	@Test
	void duplicateUsername_isRejectedByUniqueConstraint() {
		persistUser("carol");

		assertThatThrownBy(() -> userRepository.saveAndFlush(User.builder()
				.username("carol")
				.email("other@test.local")
				.passwordHash("other")
				.build())).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void duplicateEmail_isRejectedByUniqueConstraint() {
		persistUser("dave");

		assertThatThrownBy(() -> userRepository.saveAndFlush(User.builder()
				.username("dave2")
				.email("dave@test.local")
				.passwordHash("hash")
				.build())).isInstanceOf(DataIntegrityViolationException.class);
	}
}
