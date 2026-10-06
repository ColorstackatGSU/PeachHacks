package com.peachhacks.backend.prereg;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PreRegistrationRepository extends JpaRepository<PreRegistration, UUID> {

	@Query(value = """
			select new com.peachhacks.backend.prereg.PreRegistrationView(
				p.id, p.firstName, p.lastName, p.email, p.school, p.schoolEmail, p.unsubscribed,
				case when exists (select 1 from Registration r where r.email = p.email) then true else false end,
				p.createdAt)
			from PreRegistration p
			where (lower(concat(p.firstName, ' ', p.lastName)) like :pattern or p.email like :pattern)
				and (:school = '' or p.school = :school)
			order by p.createdAt desc, p.id desc
			""", countQuery = """
			select count(p) from PreRegistration p
			where (lower(concat(p.firstName, ' ', p.lastName)) like :pattern or p.email like :pattern)
				and (:school = '' or p.school = :school)
			""")
	Page<PreRegistrationView> search(@Param("pattern") String pattern, @Param("school") String school,
			Pageable pageable);

}
