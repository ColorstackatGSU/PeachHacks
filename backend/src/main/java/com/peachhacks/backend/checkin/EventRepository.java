package com.peachhacks.backend.checkin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, UUID> {

	Optional<Event> findByGeneralTrue();

	@Query("select count(e) > 0 from Event e where lower(e.name) = lower(:name) and e.id <> :exceptId")
	boolean nameTaken(@Param("name") String name, @Param("exceptId") UUID exceptId);

	/** Each row is an Event and its check-in count; the general event comes first. */
	@Query("""
			select e, (select count(c) from CheckIn c where c.eventId = e.id) from Event e
			order by e.general desc, e.startsAt asc nulls last, lower(e.name) asc
			""")
	List<Object[]> findAllWithCounts();

}
