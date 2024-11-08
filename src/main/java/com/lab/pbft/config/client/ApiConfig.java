package com.lab.pbft.config.client;

import com.lab.pbft.util.PortUtil;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@Getter
@Setter
public class ApiConfig {

    @Autowired
    @Lazy
    private PortUtil portUtil;
    @Value("${rest.server.offset}")
    private int offset;

    private Integer apiPort = null;

    private Integer currentView = 1;

    public int getCurrentLeader() {
        return (currentView-1)%serverPopulation+portUtil.basePort();
    }

    public int getLeader(int view) {
        return (view-1)%serverPopulation+portUtil.basePort();
    }

    @Value("${server.population}")
    private Integer serverPopulation;

    @Value("${server.port.pool}")
    private String portPool;

    @Value("${rest.server.url}")
    private String restServerUrl;

    private String restServerUrlWithPort;

}
