package com.lab.pbft;

import com.lab.pbft.service.SocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@Slf4j
@EntityScan(basePackages = "com.lab.pbft.model")
public class PbftApplication {

	public static void main(String[] args) {
		SpringApplication.run(PbftApplication.class, args);
	}

	@Bean
	CommandLineRunner startServer(SocketService socketService){
		return args -> {
			socketService.startServerSocket();
		};
	}

}
