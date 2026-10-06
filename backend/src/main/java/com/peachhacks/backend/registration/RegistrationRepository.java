package com.peachhacks.backend.registration;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

	/** Emails are stored lower-cased; pass a lower-cased value. */
	boolean existsByEmail(String email);

	@Query(value = """
			select r from Registration r
			where (lower(concat(r.firstName, ' ', r.lastName)) like :pattern or r.email like :pattern)
				and (:school = '' or r.school = :school)
				and (:anyStatus = true or r.status = :status)
			order by r.createdAt desc, r.id desc
			""", countQuery = """
			select count(r) from Registration r
			where (lower(concat(r.firstName, ' ', r.lastName)) like :pattern or r.email like :pattern)
				and (:school = '' or r.school = :school)
				and (:anyStatus = true or r.status = :status)
			""")
	Page<Registration> search(@Param("pattern") String pattern, @Param("school") String school,
			@Param("anyStatus") boolean anyStatus, @Param("status") RegistrationStatus status, Pageable pageable);

}
