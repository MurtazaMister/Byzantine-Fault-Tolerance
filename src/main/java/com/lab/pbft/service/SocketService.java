package com.lab.pbft.service;
import com.lab.pbft.config.client.ApiConfig;
import com.lab.pbft.service.client.ClientService;
import com.lab.pbft.util.CommandUtil;
import com.lab.pbft.util.PortUtil;
import com.lab.pbft.util.SocketMessageUtil;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

@Service
@Slf4j
@Getter
@Setter
@NoArgsConstructor
public class SocketService {

    @Autowired
    PortUtil portUtil;

    private long assignedPort = -1L;

    private int currentView = 1;

    private int lowWaterMark = 0;

    private int checkPointInterval = 300;

    private int waterMarkThreshold = 400;

    private ServerSocket serverSocket;
    @Autowired
    @Lazy
    private ApiConfig apiConfig;

    public int getCurrentLeader() {
        return (currentView-1)%apiConfig.getServerPopulation()+portUtil.basePort();
    }

    public int getLeader(int view) {
        return (view-1)%apiConfig.getServerPopulation()+portUtil.basePort();
    }

    @Autowired
    private SocketMessageUtil socketMessageUtil;

    @Autowired
    private CommandUtil commandUtil;

    @Autowired
    private ClientService clientService;

    @PostConstruct
    public void init() {

        List<Integer> PORT_POOL = portUtil.portPoolGenerator();

        assignedPort = portUtil.findAvailablePort(PORT_POOL);

        if (assignedPort == -1) {
            log.warn("Converting to client");
        } else {
            log.info("Assigned port: {}", assignedPort);
            log.info("Starting ServerSocket");

            try {
                serverSocket = new ServerSocket((int)assignedPort);
                log.info("Server listening on port: {}", assignedPort);
            } catch (IOException e) {
                log.trace("IOException: {}", e.getMessage());
            }
        }

    }

    public void startServerSocket() {
        if (assignedPort != -1) {
            log.info("Socket open for incoming connections and commands");

            // A thread to listen for commands on the terminal
            new Thread(() -> commandUtil.listenForCommands(assignedPort)).start();

            // A thread to listen for incoming messages
            socketMessageUtil.listenForIncomingMessages(serverSocket);

        } else {
            clientService.startClient();
        }
    }

}
