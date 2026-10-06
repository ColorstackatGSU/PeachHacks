package com.peachhacks.backend.email;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EmailCampaignRepository extends JpaRepository<EmailCampaign, UUID> {

	List<EmailCampaign> findAllByOrderByCreatedAtDesc();

	@Transactional
	@Modifying
	@Query("update EmailCampaign c set c.status = :status, c.sentCount = :sent, c.failedCount = :failed where c.id = :id")
	void updateProgress(@Param("id") UUID id, @Param("status") EmailCampaign.Status status, @Param("sent") int sent,
			@Param("failed") int failed);

	@Transactional
	@Modifying
	@Query("update EmailCampaign c set c.status = :status, c.sentCount = :sent, c.failedCount = :failed,"
			+ " c.completedAt = :completedAt where c.id = :id")
	void complete(@Param("id") UUID id, @Param("status") EmailCampaign.Status status, @Param("sent") int sent,
			@Param("failed") int failed, @Param("completedAt") Instant completedAt);

	/** Campaigns left unfinished by a previous process can never resume. */
	@Transactional
	@Modifying
	@Query("update EmailCampaign c set c.status = com.peachhacks.backend.email.EmailCampaign.Status.FAILED,"
			+ " c.completedAt = :now where c.status in (com.peachhacks.backend.email.EmailCampaign.Status.QUEUED,"
			+ " com.peachhacks.backend.email.EmailCampaign.Status.SENDING)")
	int failInterrupted(@Param("now") Instant now);

}
