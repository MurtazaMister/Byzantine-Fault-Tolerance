package com.lab.pbft.repository.primary;

import com.lab.pbft.model.primary.ReplyLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplyLogRepository extends JpaRepository<ReplyLog, String> {
    boolean existsByTimestamp(String timestamp);
    ReplyLog findByTimestamp(String timestamp);
}
