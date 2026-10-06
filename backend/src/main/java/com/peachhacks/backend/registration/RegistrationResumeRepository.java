package com.peachhacks.backend.registration;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface RegistrationResumeRepository extends Repository<RegistrationResume, UUID> {

	Optional<RegistrationResume> findById(UUID registrationId);

	List<RegistrationResume> findAllByRegistrationIdIn(Collection<UUID> registrationIds);

	List<RegistrationResume> findAll();

}
