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

import com.peachhacks.backend.email.MailService;

@RestController
@RequestMapping("/admin/admins")
public class AdminAccountController {

	public record AdminItem(UUID id, String email, String name, AdminRole role, Instant createdAt) {

		static AdminItem from(Admin admin) {
			return new AdminItem(admin.getId(), admin.getEmail(), admin.getName(), admin.getRole(),
					admin.getCreatedAt());
		}

	}

	public record CreateAdminRequest(
			@NotBlank(message = "Email is required") @Email(regexp = ".+@.+\\..+",
					message = "Must be a valid email") @Size(max = 255,
							message = "Email must be at most 255 characters") String email,
			@NotBlank(message = "Name is required") @Size(max = 100,
					message = "Name must be at most 100 characters") String name,
			@NotBlank(message = "Password is required") @Size(min = AuthService.MIN_PASSWORD_LENGTH,
					max = AuthService.MAX_PASSWORD_LENGTH,
					message = "Password must be 10 to 72 characters") String password,
			String role) {

		@Override
		public String toString() {
			return "CreateAdminRequest[email=" + email + ", name=" + name + ", role=" + role + "]";
		}

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
		AdminRole role = AdminRole.parseOrDefault(request.role());
		Admin admin = authService.create(request.email(), request.name(), request.password(), role);
		if (role == AdminRole.VOLUNTEER) {
			mailService.sendVolunteerWelcome(admin.getEmail(), admin.getName(), current.name());
		}
		else {
			mailService.sendAdminWelcome(admin.getEmail(), admin.getName(), current.name());
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(AdminItem.from(admin));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AdminPrincipal current) {
		authService.delete(id, current);
		return ResponseEntity.noContent().build();
	}

}
