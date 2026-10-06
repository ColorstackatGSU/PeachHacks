package com.peachhacks.backend.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * No cookies or server sessions are used, so there is nothing for CSRF to ride on and
 * CSRF protection is disabled.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/** Admins live in our own table; this only stops Spring Boot creating its default in-memory user. */
	@Bean
	UserDetailsService userDetailsService() {
		return username -> {
			throw new UsernameNotFoundException("Not supported");
		};
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, AuthService authService) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(requests -> requests.requestMatchers(HttpMethod.OPTIONS, "/**")
				.permitAll()
				.requestMatchers("/admin/auth/login")
				.permitAll()
				.requestMatchers("/admin/auth/**", "/admin/check-in", "/admin/check-in/**")
				.hasAnyRole(AdminRole.ADMIN.name(), AdminRole.VOLUNTEER.name())
				.requestMatchers(HttpMethod.GET, "/admin/events")
				.hasAnyRole(AdminRole.ADMIN.name(), AdminRole.VOLUNTEER.name())
				// Everything else under /admin is admin-only, including routes added later.
				.requestMatchers("/admin/**")
				.hasRole(AdminRole.ADMIN.name())
				.anyRequest()
				.permitAll())
			.exceptionHandling(handling -> handling
				.authenticationEntryPoint((request, response, ex) -> writeError(response, HttpStatus.UNAUTHORIZED,
						"UNAUTHORIZED", "Sign in to continue."))
				.accessDeniedHandler((request, response, ex) -> writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN",
						"You do not have access to this resource.")))
			.addFilterBefore(new BearerTokenFilter(authService), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	private static void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
	}

}
