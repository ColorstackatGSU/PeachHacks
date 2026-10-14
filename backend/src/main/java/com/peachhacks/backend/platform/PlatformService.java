package com.peachhacks.backend.platform;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.peachhacks.backend.checkin.CheckInService;
import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.Texts;
import com.peachhacks.backend.config.PlatformProperties;
import com.peachhacks.backend.registration.Registration;
import com.peachhacks.backend.registration.RegistrationRepository;
import com.peachhacks.backend.ticket.Tickets;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What a signed-in hacker sees and does: their own profile and ticket, the directory of
 * other hackers, and teams. Everyone shown is ACCEPTED; the directory holds only people
 * who have signed in to the platform and left themselves listed.
 */
@Service
public class PlatformService {

	public record Profile(String bio, String githubUrl, String linkedinUrl, boolean lookingForTeam, boolean listed) {
	}

	public record Ticket(String token, String url, String googleWalletUrl, boolean checkedIn) {
	}

	public record Me(UUID id, String firstName, String lastName, String email, String school, Profile profile,
			String discordUsername, boolean hasPassword, Ticket ticket, UUID teamId) {
	}

	public record ProfileUpdate(String bio, String githubUrl, String linkedinUrl, Boolean lookingForTeam,
			Boolean listed) {
	}

	/** lookingForTeam is false for anyone who is on a team. */
	public record Hacker(UUID id, String firstName, String lastName, String school, String bio, String discordUsername,
			String githubUrl, String linkedinUrl, boolean lookingForTeam, UUID teamId, String teamName) {
	}

	public record Member(UUID id, String firstName, String lastName, String school, String discordUsername,
			boolean owner) {
	}

	public record JoinRequest(UUID id, UUID hackerId, String firstName, String lastName, String school,
			String discordUsername, String message) {
	}

	/**
	 * requested says whether the caller has asked to join. requests is filled only for the
	 * caller's own team.
	 */
	public record Team(UUID id, String name, String description, List<Member> members, int openSpots,
			boolean requested, List<JoinRequest> requests) {
	}

	public record TeamRequest(String name, String description) {
	}

	private static final Pattern GITHUB = Pattern.compile("https://(www\\.)?github\\.com/[A-Za-z0-9-]{1,39}/?");

	private static final Pattern LINKEDIN = Pattern.compile("https://([a-z]{2,3}\\.)?linkedin\\.com/\\S{1,170}");

	private static final int MAX_TEXT = 280;

	private static final int MAX_OPEN_REQUESTS = 5;

	private final JdbcClient jdbc;

	private final RegistrationRepository registrations;

	private final Tickets tickets;

	private final CheckInService checkIns;

	private final PlatformProperties properties;

	private final TransactionTemplate transaction;

	public PlatformService(JdbcClient jdbc, RegistrationRepository registrations, Tickets tickets,
			CheckInService checkIns, PlatformProperties properties, PlatformTransactionManager transactionManager) {
		this.jdbc = jdbc;
		this.registrations = registrations;
		this.tickets = tickets;
		this.checkIns = checkIns;
		this.properties = properties;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	public Me me(UUID id) {
		Registration r = registrations.findById(id).orElseThrow(() -> ApiException.notFound("Registration not found."));
		Tickets.Links links = tickets.links(r);
		return jdbc.sql("""
				select a.bio, a.github_url, a.linkedin_url, a.looking_for_team, a.listed,
					a.password_hash is not null as has_password, l.discord_username, m.team_id
				from hacker_accounts a
				left join discord_links l on l.registration_id = a.registration_id
				left join team_members m on m.registration_id = a.registration_id
				where a.registration_id = :id
				""")
			.param("id", id)
			.query((rs, rowNum) -> new Me(id, r.getFirstName(), r.getLastName(), r.getEmail(), r.getSchool(),
					new Profile(rs.getString("bio"), rs.getString("github_url"), rs.getString("linkedin_url"),
							rs.getBoolean("looking_for_team") && rs.getObject("team_id") == null,
							rs.getBoolean("listed")),
					rs.getString("discord_username"), rs.getBoolean("has_password"),
					new Ticket(r.getTicketToken(), links.url(), links.googleWalletUrl(),
							checkIns.generalCheckedIn(id)),
					rs.getObject("team_id", UUID.class)))
			.single();
	}

	public void updateProfile(UUID id, ProfileUpdate update) {
		String bio = limited("bio", update.bio(), "Bio");
		String github = link("githubUrl", update.githubUrl(), GITHUB, "Use your profile address, like https://github.com/you");
		String linkedin = link("linkedinUrl", update.linkedinUrl(), LINKEDIN,
				"Use your profile address, like https://www.linkedin.com/in/you");
		jdbc.sql("""
				update hacker_accounts set bio = :bio, github_url = :github, linkedin_url = :linkedin,
					looking_for_team = coalesce(cast(:looking as boolean), looking_for_team),
					listed = coalesce(cast(:listed as boolean), listed)
				where registration_id = :id
				""")
			.param("bio", bio)
			.param("github", github)
			.param("linkedin", linkedin)
			.param("looking", update.lookingForTeam())
			.param("listed", update.listed())
			.param("id", id)
			.update();
	}

	public List<Hacker> hackers() {
		return jdbc.sql("""
				select r.id, r.first_name, r.last_name, r.school, a.bio, a.github_url, a.linkedin_url,
					a.looking_for_team, l.discord_username, t.id as team_id, t.name as team_name
				from hacker_accounts a
				join registrations r on r.id = a.registration_id
				left join discord_links l on l.registration_id = r.id
				left join team_members m on m.registration_id = r.id
				left join teams t on t.id = m.team_id
				where r.status = 'ACCEPTED' and a.listed
				order by lower(r.first_name), lower(r.last_name), r.id
				""")
			.query((rs, rowNum) -> new Hacker(rs.getObject("id", UUID.class), rs.getString("first_name"),
					rs.getString("last_name"), rs.getString("school"), rs.getString("bio"),
					rs.getString("discord_username"), rs.getString("github_url"), rs.getString("linkedin_url"),
					rs.getBoolean("looking_for_team") && rs.getObject("team_id") == null,
					rs.getObject("team_id", UUID.class), rs.getString("team_name")))
			.list();
	}

	/** Every team, fullest last so the ones with room come first; the caller's own team leads. */
	public List<Team> teams(UUID callerId) {
		record Row(UUID id, String name, String description, UUID ownerId) {
		}
		List<Row> rows = jdbc.sql("select id, name, description, owner_id from teams order by lower(name)")
			.query((rs, rowNum) -> new Row(rs.getObject("id", UUID.class), rs.getString("name"),
					rs.getString("description"), rs.getObject("owner_id", UUID.class)))
			.list();
		Map<UUID, List<Member>> members = new LinkedHashMap<>();
		jdbc.sql("""
				select m.team_id, r.id, r.first_name, r.last_name, r.school, l.discord_username, t.owner_id = r.id as owner
				from team_members m
				join teams t on t.id = m.team_id
				join registrations r on r.id = m.registration_id
				left join discord_links l on l.registration_id = r.id
				where r.status = 'ACCEPTED'
				order by owner desc, m.joined_at, r.id
				""").query((rs, rowNum) -> {
			members.computeIfAbsent(rs.getObject("team_id", UUID.class), key -> new ArrayList<>())
				.add(new Member(rs.getObject("id", UUID.class), rs.getString("first_name"), rs.getString("last_name"),
						rs.getString("school"), rs.getString("discord_username"), rs.getBoolean("owner")));
			return rowNum;
		}).list();
		List<UUID> requested = jdbc.sql("select team_id from team_join_requests where registration_id = :id")
			.param("id", callerId)
			.query(UUID.class)
			.list();
		UUID own = teamOf(callerId).orElse(null);
		List<Team> teams = new ArrayList<>();
		for (Row row : rows) {
			List<Member> team = members.getOrDefault(row.id(), List.of());
			boolean mine = row.id().equals(own);
			teams.add(new Team(row.id(), row.name(), row.description(), team,
					Math.max(0, properties.maxTeamSize() - team.size()), requested.contains(row.id()),
					mine ? requests(row.id()) : List.of()));
		}
		teams.sort((a, b) -> {
			if (a.id().equals(own) != b.id().equals(own)) {
				return a.id().equals(own) ? -1 : 1;
			}
			return Boolean.compare(a.openSpots() == 0, b.openSpots() == 0);
		});
		return teams;
	}

	public UUID createTeam(UUID callerId, TeamRequest request) {
		String name = teamName(request.name());
		String description = limited("description", request.description(), "Description");
		UUID teamId = UUID.randomUUID();
		try {
			transaction.executeWithoutResult(tx -> {
				if (teamOf(callerId).isPresent()) {
					throw conflict("ALREADY_ON_TEAM", "You are already on a team. Leave it before starting another.");
				}
				jdbc.sql("insert into teams (id, name, description, owner_id) values (:id, :name, :description, :owner)")
					.param("id", teamId)
					.param("name", name)
					.param("description", description)
					.param("owner", callerId)
					.update();
				join(teamId, callerId);
			});
		}
		catch (DuplicateKeyException ex) {
			throw ApiException.invalidField("name", "There is already a team with that name.");
		}
		return teamId;
	}

	public void updateTeam(UUID callerId, TeamRequest request) {
		String name = teamName(request.name());
		String description = limited("description", request.description(), "Description");
		UUID teamId = ownedTeam(callerId);
		try {
			jdbc.sql("update teams set name = :name, description = :description where id = :id")
				.param("name", name)
				.param("description", description)
				.param("id", teamId)
				.update();
		}
		catch (DuplicateKeyException ex) {
			throw ApiException.invalidField("name", "There is already a team with that name.");
		}
	}

	public void requestToJoin(UUID callerId, UUID teamId, String message) {
		String note = limited("message", message, "Message");
		transaction.executeWithoutResult(tx -> {
			if (teamOf(callerId).isPresent()) {
				throw conflict("ALREADY_ON_TEAM", "You are already on a team.");
			}
			if (memberCount(lockTeam(teamId)) >= properties.maxTeamSize()) {
				throw conflict("TEAM_FULL", "That team is full.");
			}
			long open = jdbc.sql("select count(*) from team_join_requests where registration_id = :id")
				.param("id", callerId)
				.query(Long.class)
				.single();
			if (open >= MAX_OPEN_REQUESTS) {
				throw conflict("TOO_MANY_REQUESTS", "You have " + MAX_OPEN_REQUESTS
						+ " requests waiting already. Withdraw one before asking another team.");
			}
			jdbc.sql("""
					insert into team_join_requests (id, team_id, registration_id, message)
					values (:id, :teamId, :callerId, :message)
					on conflict (team_id, registration_id) do nothing
					""")
				.param("id", UUID.randomUUID())
				.param("teamId", teamId)
				.param("callerId", callerId)
				.param("message", note)
				.update();
		});
	}

	public void withdrawRequest(UUID callerId, UUID teamId) {
		jdbc.sql("delete from team_join_requests where team_id = :teamId and registration_id = :id")
			.param("teamId", teamId)
			.param("id", callerId)
			.update();
	}

	/** Only the team's owner answers requests. Accepting withdraws the person's other requests. */
	public void answerRequest(UUID callerId, UUID requestId, boolean accept) {
		transaction.executeWithoutResult(tx -> {
			UUID teamId = ownedTeam(callerId);
			lockTeam(teamId);
			UUID hackerId = jdbc
				.sql("delete from team_join_requests where id = :id and team_id = :teamId returning registration_id")
				.param("id", requestId)
				.param("teamId", teamId)
				.query(UUID.class)
				.optional()
				.orElseThrow(() -> ApiException.notFound("That request is no longer waiting."));
			if (!accept) {
				return;
			}
			if (memberCount(teamId) >= properties.maxTeamSize()) {
				throw conflict("TEAM_FULL", "Your team is full.");
			}
			boolean eligible = jdbc.sql("""
					select exists (select 1 from registrations r where r.id = :id and r.status = 'ACCEPTED'
						and not exists (select 1 from team_members m where m.registration_id = r.id))
					""").param("id", hackerId).query(Boolean.class).single();
			if (!eligible) {
				throw conflict("ALREADY_ON_TEAM", "That hacker has joined another team in the meantime.");
			}
			join(teamId, hackerId);
		});
	}

	/** When the owner leaves, the longest-standing member takes over; an empty team is removed. */
	public void leaveTeam(UUID callerId) {
		transaction.executeWithoutResult(tx -> {
			UUID teamId = teamOf(callerId).orElseThrow(() -> ApiException.notFound("You are not on a team."));
			lockTeam(teamId);
			jdbc.sql("delete from team_members where registration_id = :id").param("id", callerId).update();
			Optional<UUID> next = jdbc
				.sql("select registration_id from team_members where team_id = :teamId order by joined_at, registration_id limit 1")
				.param("teamId", teamId)
				.query(UUID.class)
				.optional();
			if (next.isEmpty()) {
				jdbc.sql("delete from teams where id = :id").param("id", teamId).update();
			}
			else {
				jdbc.sql("update teams set owner_id = :next where id = :id and owner_id = :caller")
					.param("next", next.get())
					.param("id", teamId)
					.param("caller", callerId)
					.update();
			}
		});
	}

	public void removeMember(UUID callerId, UUID memberId) {
		if (callerId.equals(memberId)) {
			throw ApiException.validation("To leave your own team, use Leave team.", null);
		}
		UUID teamId = ownedTeam(callerId);
		int removed = jdbc.sql("delete from team_members where registration_id = :id and team_id = :teamId")
			.param("id", memberId)
			.param("teamId", teamId)
			.update();
		if (removed == 0) {
			throw ApiException.notFound("That hacker is not on your team.");
		}
	}

	private void join(UUID teamId, UUID hackerId) {
		jdbc.sql("insert into team_members (registration_id, team_id) values (:id, :teamId)")
			.param("id", hackerId)
			.param("teamId", teamId)
			.update();
		jdbc.sql("delete from team_join_requests where registration_id = :id").param("id", hackerId).update();
		jdbc.sql("update hacker_accounts set looking_for_team = false where registration_id = :id")
			.param("id", hackerId)
			.update();
	}

	private List<JoinRequest> requests(UUID teamId) {
		return jdbc.sql("""
				select q.id, r.id as hacker_id, r.first_name, r.last_name, r.school, l.discord_username, q.message
				from team_join_requests q
				join registrations r on r.id = q.registration_id
				left join discord_links l on l.registration_id = r.id
				where q.team_id = :teamId and r.status = 'ACCEPTED'
				order by q.created_at, q.id
				""").param("teamId", teamId).query(PlatformService::joinRequest).list();
	}

	private static JoinRequest joinRequest(ResultSet rs, int rowNum) throws SQLException {
		return new JoinRequest(rs.getObject("id", UUID.class), rs.getObject("hacker_id", UUID.class),
				rs.getString("first_name"), rs.getString("last_name"), rs.getString("school"),
				rs.getString("discord_username"), rs.getString("message"));
	}

	private Optional<UUID> teamOf(UUID hackerId) {
		return jdbc.sql("select team_id from team_members where registration_id = :id")
			.param("id", hackerId)
			.query(UUID.class)
			.optional();
	}

	private UUID ownedTeam(UUID callerId) {
		return jdbc.sql("select id from teams where owner_id = :id")
			.param("id", callerId)
			.query(UUID.class)
			.optional()
			.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
					"Only the hacker who leads a team can do that."));
	}

	/** Holds the team row so two people cannot take the last spot at once. */
	private UUID lockTeam(UUID teamId) {
		return jdbc.sql("select id from teams where id = :id for update")
			.param("id", teamId)
			.query(UUID.class)
			.optional()
			.orElseThrow(() -> ApiException.notFound("That team no longer exists."));
	}

	private long memberCount(UUID teamId) {
		return jdbc.sql("select count(*) from team_members where team_id = :id")
			.param("id", teamId)
			.query(Long.class)
			.single();
	}

	private static String teamName(String value) {
		String name = Texts.clean(value);
		if (name == null || name.length() < 2 || name.length() > 60) {
			throw ApiException.invalidField("name", "Give the team a name of 2 to 60 characters.");
		}
		return name.replaceAll("\\s+", " ");
	}

	private static String limited(String field, String value, String label) {
		String cleaned = Texts.clean(value);
		if (cleaned != null && cleaned.length() > MAX_TEXT) {
			throw ApiException.invalidField(field, label + " can be at most " + MAX_TEXT + " characters.");
		}
		return cleaned;
	}

	private static String link(String field, String value, Pattern pattern, String message) {
		String cleaned = Texts.clean(value);
		if (cleaned != null && (cleaned.length() > 200 || !pattern.matcher(cleaned).matches())) {
			throw ApiException.invalidField(field, message);
		}
		return cleaned;
	}

	private static ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

}
