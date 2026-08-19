package com.hackathon.backend.repository;

import com.hackathon.backend.domain.Person;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {

    List<Person> findByUser_UsernameOrderByNameAsc(String username);

    List<Person> findByUser_UsernameAndNameContainingIgnoreCaseOrderByNameAsc(String username, String keyword);

    Optional<Person> findByIdAndUser_Username(Long id, String username);

    List<Person> findByIdInAndUser_Username(List<Long> ids, String username);

    Optional<Person> findByUser_UsernameAndName(String username, String name);

    long countByUser_Username(String username);
}
