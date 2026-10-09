package com.peachhacks.backend.admin;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.springframework.transaction.annotation.Transactional;

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

	/** The token is only ever handed to the account's owner (by email) or to the admin who asked for the link. */
	public record PasswordLink(Admin admin, String token, Duration validFor) {

		@Override
		public String toString() {
			return "PasswordLink[admin=" + admin.getEmail() + "]";
		}

	}

	public record PasswordLinkView(String email, String name, boolean invite) {
	}

	public static final int MIN_PASSWORD_LENGTH = 10;

	/**
	 * BCrypt only reads the first 72 bytes; longer input is rejected rather than truncated.
	 * The limit is in bytes of UTF-8, so see fitsBcrypt for anything but plain ASCII.
	 */
	public static final int MAX_PASSWORD_LENGTH = 72;

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final AdminRepository admins;

	private final AdminSessionRepository sessions;

	private final PasswordEncoder passwordEncoder;

	private final AdminProperties properties;

	private final LoginThrottle throttle;

	private final String dummyHash;

	public AuthService(AdminRepository admins, AdminSessionRepository sessions, PasswordEncoder passwordEncoder,
			AdminProperties properties, LoginThrottle throttle) {
		this.admins = admins;
		this.sessions = sessions;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
		this.throttle = throttle;
		this.dummyHash = passwordEncoder.encode(Tokens.random());
	}

	public static boolean fitsBcrypt(String password) {
		return password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_LENGTH;
	}

	private static void requireFitsBcrypt(String field, String password) {
		if (!fitsBcrypt(password)) {
			throw ApiException.invalidField(field,
					"Password is too long. Accented letters and emoji count more than once towards the limit of 72.");
		}
	}

	/**
	 * clientAddress is only logged. The throttle is per email and answers the same for an
	 * email with no account, so it does not reveal which emails have one.
	 */
	public Login login(String email, String password, String clientAddress) {
		String normalized = Texts.email(email);
		if (normalized != null && throttle.blocked(normalized)) {
			log.warn("Sign-in for {} from {} refused: too many failed attempts", forLog(normalized), clientAddress);
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
					"Too many failed sign-in attempts for this email. Wait " + throttle.windowMinutes()
							+ " minutes and try again.");
		}
		String candidate = (password != null && fitsBcrypt(password)) ? password : "";
		Optional<Admin> admin = (normalized != null) ? admins.findByEmail(normalized) : Optional.empty();
		// Always run one hash comparison so unknown emails take as long as wrong passwords.
		boolean matches = passwordEncoder.matches(candidate,
				admin.filter(found -> !found.isPending()).map(Admin::getPasswordHash).orElse(dummyHash));
		if (admin.isEmpty() || admin.get().isPending() || !matches || candidate.isEmpty()) {
			if (normalized != null) {
				throttle.failed(normalized);
			}
			log.warn("Failed sign-in for {} from {}", forLog(normalized), clientAddress);
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Incorrect email or password.");
		}
		Instant now = Instant.now();
		sessions.deleteExpired(now);
		String token = Tokens.random();
		Instant expiresAt = now.plus(properties.sessionTtl());
		sessions.save(new AdminSession(admin.get().getId(), Tokens.sha256(token), expiresAt));
		throttle.clear(normalized);
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
		requireUnused(normalized);
		try {
			Admin admin = admins.save(new Admin(normalized, name.strip(), passwordEncoder.encode(password), role));
			log.info("{} account {} created", role, normalized);
			return admin;
		}
		catch (DataIntegrityViolationException ex) {
			throw emailTaken();
		}
	}

	public PasswordLink invite(String email, String name, AdminRole role) {
		String normalized = Texts.email(email);
		requireUnused(normalized);
		try {
			PasswordLink link = issueLink(new Admin(normalized, name.strip(), null, role), properties.inviteTtl());
			log.info("{} account {} invited", role, normalized);
			return link;
		}
		catch (DataIntegrityViolationException ex) {
			throw emailTaken();
		}
	}

	private void requireUnused(String email) {
		if (admins.findByEmail(email).isPresent()) {
			throw emailTaken();
		}
	}

	private static ApiException emailTaken() {
		return ApiException.invalidField("email", "An account with this email already exists.");
	}

	public PasswordLink reinvite(UUID id) {
		Admin admin = admins.findById(id).orElseThrow(() -> ApiException.notFound("Admin not found."));
		if (!admin.isPending()) {
			throw ApiException.validation(
					"This account already has a password. Its owner can use \"Forgot password?\" on the sign-in page.",
					null);
		}
		return issueLink(admin, properties.inviteTtl());
	}

	/** Empty when no account has this email; callers must answer the same either way. */
	public Optional<PasswordLink> requestPasswordReset(String email) {
		String normalized = Texts.email(email);
		Optional<Admin> admin = (normalized != null) ? admins.findByEmail(normalized) : Optional.empty();
		return admin.map(found -> {
			log.info("Password link requested for {}", found.getEmail());
			return issueLink(found, found.isPending() ? properties.inviteTtl() : properties.passwordResetTtl());
		});
	}

	public PasswordLinkView describePasswordLink(String token) {
		Admin admin = byPasswordToken(token);
		return new PasswordLinkView(admin.getEmail(), admin.getName(), admin.isPending());
	}

	@Transactional
	public void setPassword(String token, String password) {
		requireFitsBcrypt("password", password);
		Admin admin = byPasswordToken(token);
		admin.changePassword(passwordEncoder.encode(password));
		admins.save(admin);
		sessions.deleteOthersByAdminId(admin.getId(), "");
		throttle.clear(admin.getEmail());
		log.info("Password set for {} from an emailed link", admin.getEmail());
	}

	@Transactional
	public void changePassword(AdminPrincipal current, String currentPassword, String newPassword) {
		requireFitsBcrypt("newPassword", newPassword);
		Admin admin = admins.findById(current.id()).orElseThrow(() -> ApiException.notFound("Admin not found."));
		String candidate = (currentPassword != null && fitsBcrypt(currentPassword)) ? currentPassword : "";
		if (admin.isPending() || candidate.isEmpty() || !passwordEncoder.matches(candidate, admin.getPasswordHash())) {
			throw ApiException.invalidField("currentPassword", "That is not your current password.");
		}
		admin.changePassword(passwordEncoder.encode(newPassword));
		admins.save(admin);
		sessions.deleteOthersByAdminId(admin.getId(), current.tokenHash());
		log.info("Password changed by {}", admin.getEmail());
	}

	/** A new link replaces any earlier one for the same account. */
	private PasswordLink issueLink(Admin admin, Duration validFor) {
		String token = Tokens.random();
		admin.issuePasswordToken(Tokens.sha256(token), Instant.now().plus(validFor));
		return new PasswordLink(admins.save(admin), token, validFor);
	}

	private Admin byPasswordToken(String token) {
		Optional<Admin> admin = (token == null || token.isBlank() || token.length() > 200) ? Optional.empty()
				: admins.findByPasswordToken(Tokens.sha256(token), Instant.now());
		return admin.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD_LINK",
				"This link has expired or was already used. Ask for a new one."));
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

	/** The attempted email is whatever the client sent, so line breaks are kept out of the log. */
	private static String forLog(String email) {
		if (email == null) {
			return "(no email)";
		}
		String cleaned = email.replaceAll("\\p{Cntrl}", "?");
		return (cleaned.length() > 255) ? cleaned.substring(0, 255) + "..." : cleaned;
	}

	public static AdminView view(Admin admin) {
		return new AdminView(admin.getId(), admin.getEmail(), admin.getName(), admin.getRole());
	}

}
