package com.lab.pbft.service;

import com.lab.pbft.config.client.ApiConfig;
import com.lab.pbft.service.client.ApiService;
import com.lab.pbft.service.client.ClientService;
import com.lab.pbft.util.PortUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
public class ValidationService {

    @Autowired
    private final RestTemplate restTemplate;

    @Autowired
    private ApiConfig apiConfig;

    @Autowired
    private ApiService apiService;

    @Value("${rest.server.url}")
    private String restServerUrl;

    @Value("${server.port.pool}")
    private String initPort;

    @Value("${rest.server.offset}")
    private String offset;

    @Autowired
    private PortUtil portUtil;

    @Autowired
    private ClientService clientService;

    public ValidationService(RestTemplate restTemplate, ApiConfig apiConfig) {
        this.restTemplate = restTemplate;
    }

//    public Long identifyServer(String username) {
//
//        Long id = clientService.getId(username);
//        List<Integer> portsArray = portUtil.portPoolGenerator();
//
//        int respectivePort = portsArray.get(0) + Integer.parseInt(offset) + Math.toIntExact(id) - 1;
//
//        log.info("Connected to server port {}", respectivePort);
//
//        apiConfig.setApiPort(respectivePort);
//        apiConfig.setRestServerUrlWithPort(restServerUrl + ":" + apiConfig.getApiPort());
//
//        return id;
//
//    }

    public long validate(String username, String password){
        return apiService.validate(username, password);

    }

}