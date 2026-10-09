package com.peachhacks.backend.admin;

import com.peachhacks.backend.common.Texts;
import com.peachhacks.backend.config.AdminProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final AdminProperties properties;

	private final AdminRepository admins;

	private final AuthService authService;

	public AdminBootstrap(AdminProperties properties, AdminRepository admins, AuthService authService) {
		this.properties = properties;
		this.admins = admins;
		this.authService = authService;
	}

	@Override
	public void run(ApplicationArguments args) {
		String email = Texts.email(properties.bootstrapEmail());
		String password = properties.bootstrapPassword();
		if (email == null || password == null || password.isBlank()) {
			if (admins.count() == 0) {
				log.warn("No admin accounts exist and ADMIN_BOOTSTRAP_EMAIL / ADMIN_BOOTSTRAP_PASSWORD are not set;"
						+ " nobody can sign in to the admin site");
			}
			return;
		}
		if (admins.findByEmail(email).isPresent()) {
			return;
		}
		if (password.length() < AuthService.MIN_PASSWORD_LENGTH || !AuthService.fitsBcrypt(password)) {
			log.error("ADMIN_BOOTSTRAP_PASSWORD must be {} to {} characters; bootstrap admin {} was not created",
					AuthService.MIN_PASSWORD_LENGTH, AuthService.MAX_PASSWORD_LENGTH, email);
			return;
		}
		String name = Texts.clean(properties.bootstrapName());
		authService.create(email, (name != null) ? name : "Admin", password, AdminRole.ADMIN);
		log.info("Bootstrapped admin account {}", email);
	}

}
