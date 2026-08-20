package com.hackathon.backend.repository;

import com.hackathon.backend.domain.CustomRelationship;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomRelationshipRepository extends JpaRepository<CustomRelationship, Long> {

    /** 먼저 만든 것이 위로 오게 한다. 드롭다운에서 항목 위치가 요청마다 흔들리지 않게 하려는 것. */
    List<CustomRelationship> findByUser_UsernameOrderByIdAsc(String username);

    boolean existsByUser_UsernameAndName(String username, String name);

    void deleteByUser_Username(String username);
}
