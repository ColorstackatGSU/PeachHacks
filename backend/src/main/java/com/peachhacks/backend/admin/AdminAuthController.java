package com.peachhacks.backend.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

	private final AuthService authService;

	public AdminAuthController(AuthService authService) {
		this.authService = authService;
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
