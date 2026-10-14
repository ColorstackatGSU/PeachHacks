package com.peachhacks.backend.checkin;

import com.peachhacks.backend.admin.AdminPrincipal;
import com.peachhacks.backend.admin.AdminRole;
import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.stats.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * While the "web check-in for admins only" setting is on, a volunteer is refused on the
 * check-in routes unless the request says it comes from the staff app. This steers
 * volunteers to the app; anyone can send the header, so it is not a security boundary.
 * What a volunteer may do at all is still decided in SecurityConfig.
 */
@Component
public class WebCheckInInterceptor implements HandlerInterceptor {

	public static final String CLIENT_HEADER = "X-PeachHacks-Client";

	public static final String STAFF_APP = "staff-app";

	private static final String CHECK_IN = "/admin/check-in";

	private final SettingsService settings;

	public WebCheckInInterceptor(SettingsService settings) {
		this.settings = settings;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		// The mapping the request matched, not the URI as sent, as in RateLimitInterceptor.
		String route = (request
			.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) instanceof String pattern) ? pattern : "";
		if (!route.equals(CHECK_IN) && !route.startsWith(CHECK_IN + "/")) {
			return true;
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AdminPrincipal principal)
				|| principal.role() != AdminRole.VOLUNTEER) {
			return true;
		}
		String client = request.getHeader(CLIENT_HEADER);
		if (client != null && STAFF_APP.equalsIgnoreCase(client.strip())) {
			return true;
		}
		if (settings.isWebCheckInAdminOnly()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "WEB_CHECK_IN_ADMIN_ONLY",
					"Check-in is done in the PeachHacks staff app. Ask an organizer if you need it here.");
		}
		return true;
	}

}
