package com.peachhacks.backend.registration;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

	/** Emails are stored lower-cased; pass a lower-cased value. */
	boolean existsByEmail(String email);

	Optional<Registration> findByTicketToken(String ticketToken);

	/** checkedIn is about the general event and only applies when anyAttendance is false. */
	@Query(value = """
			select r from Registration r
			where (lower(concat(r.firstName, ' ', r.lastName)) like :pattern or r.email like :pattern)
				and (:school = '' or r.school = :school)
				and (:anyStatus = true or r.status = :status)
				and (:anyAttendance = true
					or (:checkedIn = true and exists (select 1 from CheckIn c, Event e
						where c.registrationId = r.id and e.id = c.eventId and e.general = true))
					or (:checkedIn = false and not exists (select 1 from CheckIn c, Event e
						where c.registrationId = r.id and e.id = c.eventId and e.general = true)))
			order by r.createdAt desc, r.id desc
			""", countQuery = """
			select count(r) from Registration r
			where (lower(concat(r.firstName, ' ', r.lastName)) like :pattern or r.email like :pattern)
				and (:school = '' or r.school = :school)
				and (:anyStatus = true or r.status = :status)
				and (:anyAttendance = true
					or (:checkedIn = true and exists (select 1 from CheckIn c, Event e
						where c.registrationId = r.id and e.id = c.eventId and e.general = true))
					or (:checkedIn = false and not exists (select 1 from CheckIn c, Event e
						where c.registrationId = r.id and e.id = c.eventId and e.general = true)))
			""")
	Page<Registration> search(@Param("pattern") String pattern, @Param("school") String school,
			@Param("anyStatus") boolean anyStatus, @Param("status") RegistrationStatus status,
			@Param("anyAttendance") boolean anyAttendance, @Param("checkedIn") boolean checkedIn, Pageable pageable);

	/**
	 * Each row is a Registration and its CheckIn for the event (or null), people who still
	 * need checking in first so a volunteer typing a name sees them at the top.
	 */
	@Query(value = """
			select r, c from Registration r
			left join CheckIn c on c.registrationId = r.id and c.eventId = :eventId
			where lower(r.firstName) like :pattern or lower(r.lastName) like :pattern or r.email like :pattern
				or lower(concat(r.firstName, ' ', r.lastName)) like :pattern
			order by case when c.id is null then 0 else 1 end, lower(r.lastName), lower(r.firstName), r.id
			""", countQuery = """
			select count(r) from Registration r
			where lower(r.firstName) like :pattern or lower(r.lastName) like :pattern or r.email like :pattern
				or lower(concat(r.firstName, ' ', r.lastName)) like :pattern
			""")
	Page<Object[]> searchForCheckIn(@Param("eventId") UUID eventId, @Param("pattern") String pattern,
			Pageable pageable);

}
