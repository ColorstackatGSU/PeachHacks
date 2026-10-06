package com.peachhacks.backend.checkin;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {

	Optional<CheckIn> findByRegistrationIdAndEventId(UUID registrationId, UUID eventId);

	boolean existsByRegistrationIdAndEventId(UUID registrationId, UUID eventId);

	List<CheckIn> findByEventId(UUID eventId);

	List<CheckIn> findByEventIdAndRegistrationIdIn(UUID eventId, Collection<UUID> registrationIds);

	List<CheckIn> findByRegistrationId(UUID registrationId);

	long countByEventId(UUID eventId);

	/**
	 * The unique key decides when two volunteers check the same person in at once, so the
	 * first check-in's time and name are never overwritten.
	 */
	@Modifying(clearAutomatically = true)
	@Query(value = """
			insert into check_ins (id, registration_id, event_id, checked_in_at, checked_in_by)
			values (:id, :registrationId, :eventId, :at, :by)
			on conflict (registration_id, event_id) do nothing
			""", nativeQuery = true)
	int insertIfAbsent(@Param("id") UUID id, @Param("registrationId") UUID registrationId,
			@Param("eventId") UUID eventId, @Param("at") Instant at, @Param("by") String by);

	@Modifying(clearAutomatically = true)
	@Query("delete from CheckIn c where c.registrationId = :registrationId and c.eventId = :eventId")
	int deleteFor(@Param("registrationId") UUID registrationId, @Param("eventId") UUID eventId);

}
