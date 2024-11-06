package com.lab.pbft.service;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

@Service
public class ExitService {
    private final ConfigurableApplicationContext context;

    public ExitService(ConfigurableApplicationContext context) {
        this.context = context;
    }

    public void exitApplication(int exitCode) {
        SpringApplication.exit(context, ()->exitCode);
    }
}
