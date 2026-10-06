package com.peachhacks.backend.admin;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminRepository extends JpaRepository<Admin, UUID> {

	/** Emails are stored lower-cased; pass a lower-cased value. */
	Optional<Admin> findByEmail(String email);

	List<Admin> findAllByOrderByCreatedAtAsc();

	@Query("select a from Admin a, AdminSession s where s.adminId = a.id and s.tokenHash = :tokenHash"
			+ " and s.expiresAt > :now")
	Optional<Admin> findBySessionToken(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

}
