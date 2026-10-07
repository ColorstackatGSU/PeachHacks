package com.peachhacks.backend.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.peachhacks.backend.common.Patterns;
import com.peachhacks.backend.email.MailService;

@RestController
@RequestMapping("/admin/admins")
public class AdminAccountController {

	/**
	 * setPasswordUrl is only present in the answer to creating or re-inviting an account, so
	 * the admin can pass the link on if the email does not arrive.
	 */
	public record AdminItem(UUID id, String email, String name, AdminRole role, Instant createdAt, boolean pending,
			String setPasswordUrl) {

		static AdminItem from(Admin admin) {
			return from(admin, null);
		}

		static AdminItem from(Admin admin, String setPasswordUrl) {
			return new AdminItem(admin.getId(), admin.getEmail(), admin.getName(), admin.getRole(),
					admin.getCreatedAt(), admin.isPending(), setPasswordUrl);
		}

		@Override
		public String toString() {
			return "AdminItem[email=" + email + ", role=" + role + ", pending=" + pending + "]";
		}

	}

	public record CreateAdminRequest(
			@NotBlank(message = "Email is required") @Email(regexp = Patterns.EMAIL,
					message = "Must be a valid email") @Size(max = 255,
							message = "Email must be at most 255 characters") String email,
			@NotBlank(message = "Name is required") @Size(max = 100,
					message = "Name must be at most 100 characters") String name,
			@NotBlank(message = "Choose a role") String role) {
	}

	private final AuthService authService;

	private final MailService mailService;

	public AdminAccountController(AuthService authService, MailService mailService) {
		this.authService = authService;
		this.mailService = mailService;
	}

	@GetMapping
	List<AdminItem> list() {
		return authService.list().stream().map(AdminItem::from).toList();
	}

	@PostMapping
	ResponseEntity<AdminItem> create(@Valid @RequestBody CreateAdminRequest request,
			@AuthenticationPrincipal AdminPrincipal current) {
		AdminRole role = AdminRole.parse(request.role());
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(sendInvite(authService.invite(request.email(), request.name(), role), current));
	}

	@PostMapping("/{id}/invite")
	AdminItem reinvite(@PathVariable UUID id, @AuthenticationPrincipal AdminPrincipal current) {
		return sendInvite(authService.reinvite(id), current);
	}

	private AdminItem sendInvite(AuthService.PasswordLink link, AdminPrincipal current) {
		Admin admin = link.admin();
		mailService.sendInvite(admin.getEmail(), admin.getName(), admin.getRole(), current.name(), link.token(),
				link.validFor());
		return AdminItem.from(admin, mailService.adminPasswordUrl(link.token()));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AdminPrincipal current) {
		authService.delete(id, current);
		return ResponseEntity.noContent().build();
	}

}
