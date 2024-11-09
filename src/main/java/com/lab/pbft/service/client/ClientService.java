package com.lab.pbft.service.client;

import com.lab.pbft.config.KeyConfig;
import com.lab.pbft.config.client.ApiConfig;
import com.lab.pbft.networkObjects.acknowledgements.ClientReply;
import com.lab.pbft.networkObjects.acknowledgements.Reply;
import com.lab.pbft.networkObjects.communique.Request;
import com.lab.pbft.service.ExitService;
import com.lab.pbft.service.ValidationService;
import com.lab.pbft.util.ConverterUtil.ByteStringConverter;
import com.lab.pbft.util.PortUtil;
import com.lab.pbft.util.ServerStatusUtil;
import com.lab.pbft.wrapper.AckMessageWrapper;
import com.lab.pbft.wrapper.MessageWrapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Slf4j
@Getter
@Setter
public class ClientService {

    @Autowired
    private ExitService exitService;

    @Value("${client.broadcast.wait}")
    private int broadcastWait;

    @Autowired
    @Lazy
    private ValidationService validationService;

    private long userId = -1L;
    private String username;

    @Autowired
    private ApiConfig apiConfig;

    private double countOfTransactionsExecuted = 0;
    private double totalTimeInProcessingTransactions = 0;

    @Autowired
    private ApiService apiService;

//    @Autowired
//    private CsvFileService csvFileService;

    @Autowired
    private PortUtil portUtil;
    @Value("${rest.server.url}")
    private String restServerUrl;
    @Autowired
    private RestTemplate restTemplate;
    @Value("${rest.server.offset}")
    private int offset;
    @Autowired
    private ServerStatusUtil serverStatusUtil;

    @Autowired
    @Lazy
    private KeyConfig keyConfig;
    @Autowired
    @Lazy
    private CsvFileService csvFileService;
    @Autowired
    private PerformTransactionService performTransactionService;

    public void startClient(){

        apiConfig.setApiPort(apiConfig.getCurrentLeader()+offset);
        apiConfig.setRestServerUrlWithPort(restServerUrl + ":" + apiConfig.getApiPort());

        try(BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))){

            System.out.print("username:");
            username = reader.readLine();

            System.out.print("password:");
            String password = reader.readLine();

            // Validating credentials
            long validUser = validationService.validate(username, password);

            if(validUser > 0){
                log.info("User validated");
                userId = validUser;
                keyConfig.reinit();
                listenForCommands();
            }
            else {
                log.error("Error while logging in, please try again in a while");
                exitService.exitApplication(0);
                return;
            }

        } catch (Exception e) {
            log.trace("Exception: {}", e.getMessage());
        }
    }

    public Long getId(String username){
        List<Integer> portsArray = portUtil.portPoolGenerator();

        Long id = -1L;
        int finalPort = serverStatusUtil.getActiveServer();

        String url = restServerUrl+":"+finalPort+"/user/getId";

        try {

            id = restTemplate.getForObject(UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("username", username)
                    .toUriString(), Long.class);

        } catch (HttpServerErrorException e){
            if(e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE){
                log.error("Service unavailable : {}", e.getMessage());
            } else {
                log.error("Server error: {}", e.getMessage());
            }
        }
        catch (HttpClientErrorException e) {

            if(e.getStatusCode().value() == 404){
                return -1L;
            }
            else{
                log.error(e.getMessage());
            }

        } catch (Exception e) {
            log.error(e.getMessage());
        }

        return id;
    }

    private void listenForCommands(){
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))){
            String input;
            boolean logoutFlag = false, exitFlag = false;
            while(true){
                System.out.println(String.format("""
                        Enter commands %s:
                        - l (logout)
                        - b (check balance)
                        - s (send) <receiver_userID> <amount>
                        - e (exit)
                        - f (fail server) <port (optional)>
                        - r (resume server) <port (optional)>
                        - i (infect/byzantine server) <port (optional)>
                        - x (execute test file) <path (optional)>
                        """, username));
                input = reader.readLine();

                if(input != null){
                    String[] parts = input.split(" ");

                    switch(parts[0]){
                        case "l":
                            userId = -1L;
                            logoutFlag = true;
                            break;
                        case "b":
                            System.out.println("Balance: $"+apiService.balanceCheck(userId));
                            break;
                        case "e":
                            exitFlag = true;
                            break;
                        case "f":
                            if(parts.length > 1){
                                int port = Integer.parseInt(parts[1]);
                                apiService.failServer(port);
                            }
                            else{
                                apiService.failServer(null);
                            }
                            break;
                        case "r":
                            if(parts.length > 1){
                                int port = Integer.parseInt(parts[1]);
                                apiService.resumeServer(port);
                            }
                            else{
                                apiService.resumeServer(null);
                            }
                            break;
                        case "i":
                            if(parts.length > 1){
                                int port = Integer.parseInt(parts[1]);
                                apiService.byzantineServer(port);
                            }
                            else{
                                apiService.byzantineServer(null);
                            }
                            break;
                        case "s":
                            if(parts.length == 3 && parts[1].matches("\\d+") && parts[2].matches("\\d+")) {

                                Request request = Request.builder()
                                                .currentView(apiConfig.getCurrentView())
                                                .clientId(userId)
                                                .receiverId(Integer.parseInt(parts[1]))
                                                .amount(Long.parseLong(parts[2]))
                                                .timestamp(LocalDateTime.now().toString())
                                                .build();

                                request.signMessage(keyConfig.getPrivateKey());

                                performTransactionService.performTransaction(request);
                            }
                            else {
                                log.warn("Invalid command");
                            }
                            break;
                        case "x":
                            if(parts.length > 1){
                                String filePath = "";
                                for(int i = 1;i<parts.length;i++) {
                                    filePath += parts[i] + " ";
                                }
                                csvFileService.readAndExecuteCsvFile(filePath.trim(), reader);
                            }
                            else{
                                csvFileService.readAndExecuteCsvFile(reader);
                            }
                            break;
                        default:
                            log.warn("Unknown command: {}", parts[0]);
                            break;
                    }

                    if(logoutFlag || exitFlag){
                        break;
                    }
                }
            }
            if(logoutFlag){
                log.info("Logged out successfully. Login again.");
                startClient();
            } else if (exitFlag) {
                log.info("Have a great day!");
                exitService.exitApplication(0);
            }
        }
        catch(Exception e){
            log.trace("Error reading input: {}", e.getMessage());
        }
    }
}
