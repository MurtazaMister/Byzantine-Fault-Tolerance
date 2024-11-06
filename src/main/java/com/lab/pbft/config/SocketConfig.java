package com.lab.pbft.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@Getter
public class SocketConfig {
    @Value("${socket.connection.timeout}")
    private int connectionTimeout;
    @Value("${socket.read.timeout}")
    private int readTimeout;
}
