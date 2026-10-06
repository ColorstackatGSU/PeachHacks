package com.peachhacks.backend.admin;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerTokenFilter extends OncePerRequestFilter {

	private static final String PREFIX = "Bearer ";

	private final AuthService authService;

	public BearerTokenFilter(AuthService authService) {
		this.authService = authService;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith(request.getContextPath() + "/admin/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
			authService.authenticate(header.substring(PREFIX.length()).trim()).ifPresent(principal -> {
				UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken
					.authenticated(principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
				SecurityContext context = SecurityContextHolder.createEmptyContext();
				context.setAuthentication(authentication);
				SecurityContextHolder.setContext(context);
			});
		}
		chain.doFilter(request, response);
	}

}
