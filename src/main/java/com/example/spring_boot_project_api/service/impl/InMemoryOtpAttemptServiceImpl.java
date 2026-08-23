package com.example.spring_boot_project_api.service.impl;

import com.example.spring_boot_project_api.config.TwoFactorProperties;
import com.example.spring_boot_project_api.exception.TwoFactorLockedException;
import com.example.spring_boot_project_api.service.OtpAttemptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-node in-memory implementation:
 * <ul>
 *   <li>rolling failure window per user; exceeding {@code max-failures} locks the account
 *       for {@code lock-duration}</li>
 *   <li>consumed pending-token jtis, evicted lazily once expired</li>
 * </ul>
 *
 * <p>TODO: replace with a shared store (Redis/DB) when running multiple instances.</p>
 */
@Service
public class InMemoryOtpAttemptServiceImpl implements OtpAttemptService {

	private record Attempts(int failures, Instant windowStart, Instant lockedUntil) {
	}

	private final TwoFactorProperties props;
	private final Clock clock;
	private final Map<Long, Attempts> failuresByUser = new ConcurrentHashMap<>();
	private final Map<String, Instant> consumedJtis = new ConcurrentHashMap<>();

	/** Primary constructor used by Spring. */
	@Autowired
	public InMemoryOtpAttemptServiceImpl(TwoFactorProperties props) {
		this(props, Clock.systemUTC());
	}

	public InMemoryOtpAttemptServiceImpl(TwoFactorProperties props, Clock clock) {
		this.props = props;
		this.clock = clock;
	}

	@Override
	public void assertAttemptsAllowed(Long userId) {
		purgeExpired();
		Attempts attempts = failuresByUser.get(userId);
		if (attempts != null && attempts.lockedUntil() != null) {
			Instant now = clock.instant();
			if (attempts.lockedUntil().isAfter(now)) {
				throw new TwoFactorLockedException(attempts.lockedUntil().getEpochSecond() - now.getEpochSecond());
			}
			failuresByUser.remove(userId);
		}
	}

	@Override
	public void recordFailure(Long userId) {
		Instant now = clock.instant();
		failuresByUser.compute(userId, (id, current) -> {
			boolean freshWindow = current == null
					|| now.isAfter(current.windowStart().plus(props.failureWindow()));
			int failures = freshWindow ? 1 : current.failures() + 1;
			Instant lockedUntil = failures >= props.maxFailures() ? now.plus(props.lockDuration()) : null;
			return new Attempts(failures, freshWindow ? now : current.windowStart(), lockedUntil);
		});
	}

	@Override
	public void resetFailures(Long userId) {
		failuresByUser.remove(userId);
	}

	@Override
	public boolean isPendingTokenConsumed(String jti) {
		if (jti == null) {
			return false;
		}
		purgeExpired();
		return consumedJtis.containsKey(jti);
	}

	@Override
	public void markPendingTokenConsumed(String jti, Instant expiresAt) {
		consumedJtis.putIfAbsent(jti, expiresAt);
	}

	private void purgeExpired() {
		Instant now = clock.instant();
		Iterator<Map.Entry<String, Instant>> it = consumedJtis.entrySet().iterator();
		while (it.hasNext()) {
			if (!it.next().getValue().isAfter(now)) {
				it.remove();
			}
		}
	}
}
