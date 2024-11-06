package com.lab.pbft.repository.primary;

import com.lab.pbft.model.primary.Log;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LogRepository extends JpaRepository<Log, Long> {

    Optional<Log> findTopByOrderBySequenceNumberDesc();

    boolean existsBySequenceNumber(Long sequenceNumber);

    Optional<Log> findBySequenceNumber(Long sequenceNumber);

}
