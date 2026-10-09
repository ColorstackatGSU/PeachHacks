package com.peachhacks.backend.admin;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AdminSessionRepository extends JpaRepository<AdminSession, UUID> {

	@Transactional
	@Modifying
	@Query("delete from AdminSession s where s.tokenHash = :tokenHash")
	int deleteByTokenHash(@Param("tokenHash") String tokenHash);

	@Transactional
	@Modifying
	@Query("delete from AdminSession s where s.adminId = :adminId and s.tokenHash <> :keepTokenHash")
	int deleteOthersByAdminId(@Param("adminId") UUID adminId, @Param("keepTokenHash") String keepTokenHash);

	@Transactional
	@Modifying
	@Query("delete from AdminSession s where s.expiresAt <= :now")
	int deleteExpired(@Param("now") Instant now);

}
