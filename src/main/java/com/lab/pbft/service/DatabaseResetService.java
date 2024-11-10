package com.lab.pbft.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DatabaseResetService {

    private String[] tables = {"log", "replyLog", "new_view_bundle", "new_view"};

    private final JdbcTemplate primaryJdbcTemplate;

    @Autowired
    SocketService socketService;

    @Value("${app.developer-mode}")
    private boolean developerMode;

    public DatabaseResetService(@Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate){
        this.primaryJdbcTemplate = primaryJdbcTemplate;
    }

    @PostConstruct
    public void init() {
        if (developerMode && socketService.getAssignedPort() > 0) {
            log.warn("Resetting balances & transactions");

            // Removing foreign key checks
            primaryJdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0;");

            // Dropping all tables
            for (String table : tables) {
                primaryJdbcTemplate.execute("DELETE FROM " + table);
                primaryJdbcTemplate.execute("ALTER TABLE " + table + " AUTO_INCREMENT = 1");
            }

            // Reviving foreign key checks
            primaryJdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1;");
            primaryJdbcTemplate.execute("update user_account set balance = 10 where id>0;");

            log.warn("Balances reset");
        }
    }
}