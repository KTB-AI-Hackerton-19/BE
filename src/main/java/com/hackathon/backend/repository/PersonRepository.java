package com.hackathon.backend.repository;

import com.hackathon.backend.domain.Person;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {

    List<Person> findByUser_UsernameOrderByNameAsc(String username);

    /** 사람이 수십 명 넘어가면 목록이 무거워지므로 페이지 단위로 끊는다. */
    Page<Person> findByUser_UsernameOrderByNameAsc(String username, Pageable pageable);

    Page<Person> findByUser_UsernameAndNameContainingIgnoreCaseOrderByNameAsc(
            String username, String keyword, Pageable pageable);

    List<Person> findByUser_UsernameAndNameContainingIgnoreCaseOrderByNameAsc(String username, String keyword);

    Optional<Person> findByIdAndUser_Username(Long id, String username);

    List<Person> findByIdInAndUser_Username(List<Long> ids, String username);

    List<Person> findByUser_UsernameAndNameOrderByIdAsc(String username, String name);

    long countByUser_Username(String username);

    void deleteByUser_Username(String username);
}
