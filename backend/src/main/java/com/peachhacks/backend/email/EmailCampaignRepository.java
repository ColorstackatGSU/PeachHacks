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

	String UNFINISHED = "c.status in (com.peachhacks.backend.email.EmailCampaign.Status.QUEUED,"
			+ " com.peachhacks.backend.email.EmailCampaign.Status.SENDING)";

	List<EmailCampaign> findAllByOrderByCreatedAtDesc();

	@Query("select c from EmailCampaign c where " + UNFINISHED + " order by c.createdAt, c.id")
	List<EmailCampaign> findUnfinished();

	/** school is '' for a campaign that is not narrowed to one school. */
	@Query("select count(c) > 0 from EmailCampaign c where " + UNFINISHED + " and c.kind = :kind"
			+ " and c.audience = :audience and coalesce(c.school, '') = :school and c.subject = :subject"
			+ " and c.body = :body")
	boolean existsUnfinishedCopy(@Param("kind") CampaignKind kind, @Param("audience") Audience audience,
			@Param("school") String school, @Param("subject") String subject, @Param("body") String body);

	/** Leaves the counts as they are. */
	@Transactional
	@Modifying
	@Query("update EmailCampaign c set c.status = com.peachhacks.backend.email.EmailCampaign.Status.FAILED,"
			+ " c.completedAt = :now where c.id = :id")
	void fail(@Param("id") UUID id, @Param("now") Instant now);

}
