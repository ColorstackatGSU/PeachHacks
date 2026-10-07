package com.peachhacks.backend.acceptance;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.peachhacks.backend.config.AcceptanceProperties;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A registration needs an age review when its age is under the minimum for students of
 * other schools and its school is not the host school. It is information for organizers:
 * nothing is refused because of it.
 */
@Component
public class AgeReview {

	/**
	 * Written so that it is valid both as SQL and as JPQL for a registration aliased r.
	 * Binds :minimumAge, :host and :hostLength.
	 */
	public static final String NEEDED = "(r.age < :minimumAge and not (" + AcceptanceService.IS_HOST + "))";

	private final JdbcClient jdbc;

	private final int minimumAge;

	private final String host;

	public AgeReview(JdbcClient jdbc, AcceptanceProperties properties) {
		this.jdbc = jdbc;
		this.minimumAge = properties.nonHostMinimumAge();
		this.host = properties.hostSchoolName().toLowerCase(Locale.ROOT);
	}

	public int minimumAge() {
		return minimumAge;
	}

	public String host() {
		return host;
	}

	public int hostLength() {
		return host.length();
	}

	public boolean needed(UUID id) {
		return !among(Set.of(id)).isEmpty();
	}

	public Set<UUID> among(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(bind(jdbc.sql("select r.id from registrations r where r.id in (:ids) and " + NEEDED)
			.param("ids", ids)).query(UUID.class).list());
	}

	public Set<UUID> all() {
		return new HashSet<>(
				bind(jdbc.sql("select r.id from registrations r where " + NEEDED)).query(UUID.class).list());
	}

	private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement) {
		return statement.param("minimumAge", minimumAge).param("host", host).param("hostLength", host.length());
	}

}
