package com.example.spring_boot_project_api.repository;

import com.example.spring_boot_project_api.model.BackupCode;
import com.example.spring_boot_project_api.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private BackupCodeRepository backupCodeRepository;
	@Autowired
	private jakarta.persistence.EntityManager entityManager;

	private User persistUser(String username) {
		User user = userRepository.save(User.builder()
				.username(username)
				.passwordHash("hash")
				.twoFactorEnabled(false)
				.build());
		entityManager.flush();
		return user;
	}

	@Test
	void save_defaultsTwoFactorDisabled_andPersistsHash() {
		User saved = persistUser("alice");

		User reloaded = userRepository.findById(saved.getId()).orElseThrow();
		assertThat(reloaded.getUsername()).isEqualTo("alice");
		assertThat(reloaded.getPasswordHash()).isEqualTo("hash");
		assertThat(reloaded.isTwoFactorEnabled()).isFalse();
	}

	@Test
	void duplicateUsername_isRejectedByUniqueConstraint() {
		persistUser("bob");

		assertThatThrownBy(() -> {
			userRepository.saveAndFlush(User.builder()
					.username("bob")
					.passwordHash("other")
					.twoFactorEnabled(false)
					.build());
		}).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void findByUsername_findsPersistedUser() {
		persistUser("carol");

		assertThat(userRepository.findByUsername("carol")).isPresent();
		assertThat(userRepository.findByUsername("nobody")).isEmpty();
	}

	@Test
	void backupCodes_unusedFiltering_andDeleteByUser() {
		User user = persistUser("dave");
		LocalDateTime now = LocalDateTime.now();

		backupCodeRepository.save(BackupCode.builder()
				.userId(user.getId()).codeHash("h1").createdAt(now).build());
		BackupCode used = backupCodeRepository.save(BackupCode.builder()
				.userId(user.getId()).codeHash("h2").createdAt(now).build());
		used.setUsedAt(now);
		backupCodeRepository.save(used);

		List<BackupCode> unused = backupCodeRepository.findByUserIdAndUsedAtIsNull(user.getId());
		assertThat(unused).hasSize(1);
		assertThat(unused.getFirst().getCodeHash()).isEqualTo("h1");

		backupCodeRepository.deleteByUserId(user.getId());
		assertThat(backupCodeRepository.findByUserIdAndUsedAtIsNull(user.getId())).isEmpty();
	}
}
