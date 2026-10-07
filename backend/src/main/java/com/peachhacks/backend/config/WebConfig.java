package com.peachhacks.backend.config;

import java.util.List;

import com.peachhacks.backend.common.RateLimitInterceptor;
import com.peachhacks.backend.registration.RegistrationBodyLimitInterceptor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	private final RateLimitInterceptor rateLimitInterceptor;

	private final RegistrationBodyLimitInterceptor registrationBodyLimit;

	public WebConfig(RateLimitInterceptor rateLimitInterceptor,
			RegistrationBodyLimitInterceptor registrationBodyLimit) {
		this.rateLimitInterceptor = rateLimitInterceptor;
		this.registrationBodyLimit = registrationBodyLimit;
	}

	/** Picked up by Spring Security's CORS filter, so preflight requests are answered before authentication. */
	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(corsProperties.allowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setExposedHeaders(List.of("Content-Disposition"));
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/public/**", "/admin/auth/login", "/admin/auth/forgot-password", "/admin/auth/set-password",
					"/admin/auth/set-password/check");
		registry.addInterceptor(registrationBodyLimit).addPathPatterns("/public/registrations");
	}

}
