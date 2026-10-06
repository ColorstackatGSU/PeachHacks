package com.peachhacks.backend.admin;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.Texts;
import com.peachhacks.backend.common.Tokens;
import com.peachhacks.backend.config.AdminProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

	public record AdminView(UUID id, String email, String name, AdminRole role) {
	}

	public record Login(String token, Instant expiresAt, AdminView admin) {

		@Override
		public String toString() {
			return "Login[admin=" + admin + ", expiresAt=" + expiresAt + "]";
		}

	}

	public static final int MIN_PASSWORD_LENGTH = 10;

	/** BCrypt only reads the first 72 bytes; longer input is rejected rather than truncated. */
	public static final int MAX_PASSWORD_LENGTH = 72;

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final AdminRepository admins;

	private final AdminSessionRepository sessions;

	private final PasswordEncoder passwordEncoder;

	private final AdminProperties properties;

	private final String dummyHash;

	public AuthService(AdminRepository admins, AdminSessionRepository sessions, PasswordEncoder passwordEncoder,
			AdminProperties properties) {
		this.admins = admins;
		this.sessions = sessions;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
		this.dummyHash = passwordEncoder.encode(Tokens.random());
	}

	public Login login(String email, String password) {
		String normalized = Texts.email(email);
		String candidate = (password != null && password.length() <= MAX_PASSWORD_LENGTH) ? password : "";
		Optional<Admin> admin = (normalized != null) ? admins.findByEmail(normalized) : Optional.empty();
		// Always run one hash comparison so unknown emails take as long as wrong passwords.
		boolean matches = passwordEncoder.matches(candidate, admin.map(Admin::getPasswordHash).orElse(dummyHash));
		if (admin.isEmpty() || !matches || candidate.isEmpty()) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Incorrect email or password.");
		}
		Instant now = Instant.now();
		sessions.deleteExpired(now);
		String token = Tokens.random();
		Instant expiresAt = now.plus(properties.sessionTtl());
		sessions.save(new AdminSession(admin.get().getId(), Tokens.sha256(token), expiresAt));
		log.info("Admin {} signed in", admin.get().getEmail());
		return new Login(token, expiresAt, view(admin.get()));
	}

	public Optional<AdminPrincipal> authenticate(String token) {
		if (token == null || token.isBlank() || token.length() > 200) {
			return Optional.empty();
		}
		String tokenHash = Tokens.sha256(token);
		return admins.findBySessionToken(tokenHash, Instant.now())
			.map(admin -> new AdminPrincipal(admin.getId(), admin.getEmail(), admin.getName(), admin.getRole(),
					tokenHash));
	}

	public void logout(AdminPrincipal principal) {
		sessions.deleteByTokenHash(principal.tokenHash());
	}

	public List<Admin> list() {
		return admins.findAllByOrderByCreatedAtAsc();
	}

	public Admin create(String email, String name, String password, AdminRole role) {
		String normalized = Texts.email(email);
		if (admins.findByEmail(normalized).isPresent()) {
			throw ApiException.invalidField("email", "An account with this email already exists.");
		}
		try {
			Admin admin = admins.save(new Admin(normalized, name.strip(), passwordEncoder.encode(password), role));
			log.info("{} account {} created", role, normalized);
			return admin;
		}
		catch (DataIntegrityViolationException ex) {
			throw ApiException.invalidField("email", "An account with this email already exists.");
		}
	}

	public void delete(UUID id, AdminPrincipal current) {
		Admin target = admins.findById(id).orElseThrow(() -> ApiException.notFound("Admin not found."));
		if (id.equals(current.id())) {
			throw ApiException.validation("You cannot delete your own account.", null);
		}
		if (target.getRole() == AdminRole.ADMIN && admins.countByRole(AdminRole.ADMIN) <= 1) {
			throw ApiException.validation("The last admin account cannot be deleted.", null);
		}
		// Sessions are removed by the foreign key's cascade.
		admins.deleteById(id);
		log.info("Admin account {} deleted by {}", id, current.email());
	}

	public static AdminView view(Admin admin) {
		return new AdminView(admin.getId(), admin.getEmail(), admin.getName(), admin.getRole());
	}

}
