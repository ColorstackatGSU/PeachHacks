package com.peachhacks.backend.prereg;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PreRegistrationRepository extends JpaRepository<PreRegistration, UUID> {

	/** confirmed is about the school email and only applies when anyConfirmation is false. */
	@Query(value = """
			select new com.peachhacks.backend.prereg.PreRegistrationView(
				p.id, p.firstName, p.lastName, p.email, p.school, p.schoolEmail, p.unsubscribed,
				case when exists (select 1 from Registration r where r.email = p.email) then true else false end,
				p.createdAt, p.schoolEmailConfirmedAt)
			from PreRegistration p
			where (lower(concat(p.firstName, ' ', p.lastName)) like :pattern or p.email like :pattern)
				and (:school = '' or p.school = :school)
				and (:anyConfirmation = true
					or (:confirmed = true and p.schoolEmailConfirmedAt is not null)
					or (:confirmed = false and p.schoolEmailConfirmedAt is null))
			order by p.createdAt desc, p.id desc
			""", countQuery = """
			select count(p) from PreRegistration p
			where (lower(concat(p.firstName, ' ', p.lastName)) like :pattern or p.email like :pattern)
				and (:school = '' or p.school = :school)
				and (:anyConfirmation = true
					or (:confirmed = true and p.schoolEmailConfirmedAt is not null)
					or (:confirmed = false and p.schoolEmailConfirmedAt is null))
			""")
	Page<PreRegistrationView> search(@Param("pattern") String pattern, @Param("school") String school,
			@Param("anyConfirmation") boolean anyConfirmation, @Param("confirmed") boolean confirmed,
			Pageable pageable);

}
