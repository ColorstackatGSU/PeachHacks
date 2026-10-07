package com.peachhacks.backend.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.peachhacks.backend.email.MailService;

@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

	public record LoginRequest(@NotBlank(message = "Email is required") String email,
			@NotBlank(message = "Password is required") String password) {

		@Override
		public String toString() {
			return "LoginRequest[email=" + email + "]";
		}

	}

	public record ForgotPasswordRequest(@NotBlank(message = "Email is required") @Size(max = 255,
			message = "Email must be at most 255 characters") String email) {
	}

	public record PasswordLinkRequest(@NotBlank(message = "The link is incomplete") String token) {

		@Override
		public String toString() {
			return "PasswordLinkRequest[]";
		}

	}

	public record SetPasswordRequest(@NotBlank(message = "The link is incomplete") String token,
			@NotBlank(message = "Password is required") @Size(min = AuthService.MIN_PASSWORD_LENGTH,
					max = AuthService.MAX_PASSWORD_LENGTH,
					message = "Password must be 10 to 72 characters") String password) {

		@Override
		public String toString() {
			return "SetPasswordRequest[]";
		}

	}

	public record ChangePasswordRequest(@NotBlank(message = "Current password is required") String currentPassword,
			@NotBlank(message = "New password is required") @Size(min = AuthService.MIN_PASSWORD_LENGTH,
					max = AuthService.MAX_PASSWORD_LENGTH,
					message = "Password must be 10 to 72 characters") String newPassword) {

		@Override
		public String toString() {
			return "ChangePasswordRequest[]";
		}

	}

	private final AuthService authService;

	private final MailService mailService;

	public AdminAuthController(AuthService authService, MailService mailService) {
		this.authService = authService;
		this.mailService = mailService;
	}

	/** Answers the same whether or not the account exists. */
	@PostMapping("/forgot-password")
	ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		authService.requestPasswordReset(request.email()).ifPresent(link -> {
			Admin admin = link.admin();
			if (!admin.isPending()) {
				mailService.sendPasswordReset(admin.getEmail(), admin.getName(), link.token(), link.validFor());
			}
			else if (admin.getRole() == AdminRole.VOLUNTEER) {
				mailService.sendVolunteerInvite(admin.getEmail(), admin.getName(), "An organizer", link.token(),
						link.validFor());
			}
			else {
				mailService.sendAdminInvite(admin.getEmail(), admin.getName(), "An organizer", link.token(),
						link.validFor());
			}
		});
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/set-password/check")
	AuthService.PasswordLinkView checkPasswordLink(@Valid @RequestBody PasswordLinkRequest request) {
		return authService.describePasswordLink(request.token());
	}

	@PostMapping("/set-password")
	ResponseEntity<Void> setPassword(@Valid @RequestBody SetPasswordRequest request) {
		authService.setPassword(request.token(), request.password());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/change-password")
	ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
			@AuthenticationPrincipal AdminPrincipal admin) {
		authService.changePassword(admin, request.currentPassword(), request.newPassword());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/login")
	AuthService.Login login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request.email(), request.password());
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(@AuthenticationPrincipal AdminPrincipal admin) {
		authService.logout(admin);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	AuthService.AdminView me(@AuthenticationPrincipal AdminPrincipal admin) {
		return new AuthService.AdminView(admin.id(), admin.email(), admin.name(), admin.role());
	}

}
