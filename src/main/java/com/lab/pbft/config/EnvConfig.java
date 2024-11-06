package com.lab.pbft.config;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class EnvConfig {

    public EnvConfig(){
        Dotenv dotenv = Dotenv.configure().load();

        System.setProperty("db_username", dotenv.get("db_username"));
        System.setProperty("db_password", dotenv.get("db_password"));
        System.setProperty("db_url", dotenv.get("db_url"));

        log.info("System properties set");
    }
}
