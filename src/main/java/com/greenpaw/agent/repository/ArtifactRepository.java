package com.greenpaw.agent.repository;

import com.greenpaw.agent.domain.Artifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtifactRepository extends JpaRepository<Artifact, String> {
    Optional<Artifact> findByIdAndUserId(String id, String userId);
}
