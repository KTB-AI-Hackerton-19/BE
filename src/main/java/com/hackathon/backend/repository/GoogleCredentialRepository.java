package com.hackathon.backend.repository;

import com.hackathon.backend.domain.GoogleCredential;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleCredentialRepository extends JpaRepository<GoogleCredential, Long> {

    Optional<GoogleCredential> findByUser_Username(String username);

    Optional<GoogleCredential> findByUser_Id(Long userId);

    void deleteByUser_Username(String username);
}
