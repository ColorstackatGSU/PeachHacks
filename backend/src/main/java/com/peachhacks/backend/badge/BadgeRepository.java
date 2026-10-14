package com.peachhacks.backend.badge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BadgeRepository extends JpaRepository<Badge, UUID> {

	Optional<Badge> findByUidAndRevokedAtIsNull(String uid);

	Optional<Badge> findByRegistrationIdAndRevokedAtIsNull(UUID registrationId);

	boolean existsByUid(String uid);

	List<Badge> findByKindAndRevokedAtIsNullOrderByBoundAtDesc(BadgeKind kind);

	/**
	 * The two partial unique indexes decide when the same card, or the same person, is bound
	 * twice at once. The loser inserts nothing and its transaction stays usable, so it can
	 * read what the winner wrote.
	 */
	@Modifying(clearAutomatically = true)
	@Query(value = """
			insert into badges (id, uid, registration_id, bound_at, bound_by)
			values (:id, :uid, :registrationId, :at, :by)
			on conflict do nothing
			""", nativeQuery = true)
	int insertIfAbsent(@Param("id") UUID id, @Param("uid") String uid, @Param("registrationId") UUID registrationId,
			@Param("at") Instant at, @Param("by") String by);

	/** Decided by the per-card index alone, in the same way. */
	@Modifying(clearAutomatically = true)
	@Query(value = """
			insert into badges (id, uid, kind, bound_at, bound_by)
			values (:id, :uid, 'SPONSOR', :at, :by)
			on conflict do nothing
			""", nativeQuery = true)
	int insertSponsorIfAbsent(@Param("id") UUID id, @Param("uid") String uid, @Param("at") Instant at,
			@Param("by") String by);

	/** A sponsor badge has no registration, so this never touches one. */
	@Modifying(clearAutomatically = true)
	@Query(value = """
			update badges set revoked_at = :at, revoked_by = :by
			where registration_id = :registrationId and revoked_at is null
			""", nativeQuery = true)
	int revokeActive(@Param("registrationId") UUID registrationId, @Param("at") Instant at, @Param("by") String by);

	@Modifying(clearAutomatically = true)
	@Query(value = """
			update badges set revoked_at = :at, revoked_by = :by
			where uid = :uid and kind = 'SPONSOR' and revoked_at is null
			""", nativeQuery = true)
	int revokeActiveSponsor(@Param("uid") String uid, @Param("at") Instant at, @Param("by") String by);

}
