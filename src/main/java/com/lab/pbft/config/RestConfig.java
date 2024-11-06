package com.lab.pbft.config;

import com.lab.pbft.service.SocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.ConfigurableTomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableWebMvc
@Slf4j
@DependsOn("socketService")
public class RestConfig {

    @Autowired
    private SocketService socketService;

    @Value("${rest.server.offset}")
    private String offset;

    @Bean
    public WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory> webServerCustomizer() {
        return socketService.getAssignedPort() > 0 ? factory -> {
            long socketPort = socketService.getAssignedPort();
            long restPort = socketPort + Integer.parseInt(offset);
            log.info("Rest port identified: {}", restPort);
            factory.setPort((int)restPort);
        } : null;
    }
}
