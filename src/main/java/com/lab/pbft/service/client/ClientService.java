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
                        - // x (execute test file) <path (optional)>
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

                                ClientReply clientReply = apiService.transact(request);

                                if(clientReply == null) {
                                    System.out.println("Transaction failed");
                                }
                                else if(clientReply.getCurrentView() == -1){
                                    try {
                                        System.out.println("Didn't receive reply, broadcasting request after "+broadcastWait+" ms");
                                        Thread.sleep(broadcastWait);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    System.out.println("Broadcasting");

                                    List<AckMessageWrapper> acks = apiService.retransact(request);

                                    int signatureCount = 0;
                                    Map<String, Integer> receipts = new HashMap<>();

                                    for(AckMessageWrapper ack : acks) {
                                        Reply reply = ack.getReply();

                                        if (reply.verifyMessage(keyConfig.getPublicKeyStore().get(ack.getFromPort()))){
                                            receipts.put(reply.getReplyDigest(), receipts.getOrDefault(reply.getReplyDigest(), 0) + 1);
                                        }
                                    }

                                    int threshold = (apiConfig.getServerPopulation() - 1) / 3 + 1;
                                    String finalDigest = null;

                                    for(String x : receipts.keySet()) {
                                        if(receipts.get(x) >= threshold) {
                                            finalDigest = x;
                                            signatureCount = receipts.get(x);
                                            threshold = signatureCount;
                                        }
                                    }

                                    Reply finalReply = null;
                                    for(AckMessageWrapper ack : acks) {
                                        if(ack.getReply().getReplyDigest().equals(finalDigest)) {
                                            finalReply = ack.getReply();
                                            break;
                                        }
                                    }

                                    if (finalReply != null && signatureCount != 0) {
                                        System.out.println("Verified at least f+1 signatures in re-transact, received " + signatureCount + " signatures");
                                        System.out.println("Transaction status: "+((finalReply.isApproved())?"Approved":"Failed"));
                                        System.out.println("Final balance = $" + finalReply.getFinalBalance() + "\nAfter executing "+((finalReply.isApproved())?"approved":"failed")+" transaction $" + request.getAmount() + " : " + request.getClientId() + " -> " + request.getReceiverId());
                                        if(apiConfig.getCurrentView() != finalReply.getCurrentView()){
                                            System.out.println("View changed, current view: "+ finalReply.getCurrentView());
                                        }
                                        apiConfig.setCurrentView((int)finalReply.getCurrentView());
                                    } else {
                                        System.out.println("Received unverified reply with only "+signatureCount+" signatures for transaction " + request.getAmount() + " : " + request.getClientId() + " -> " + request.getReceiverId());
                                    }

                                }
                                else{
                                    if(!clientReply.getTimestamp().equals(request.getTimestamp())){
                                        System.out.println("Timestamps don't match, invalid reply received");
                                    }
                                    else {

                                        // Verify the client reply signatures
                                        int signatureCount = 0;

                                        for (long id : clientReply.getSignatures().keySet()) {
                                            if (clientReply.verifyMessage(clientReply.getSignatures().get(id), keyConfig.getPublicKeyStore().get(id)))
                                                signatureCount++;
                                        }

                                        int threshold = (apiConfig.getServerPopulation() - 1) / 3 + 1;

                                        if (signatureCount >= threshold) {
                                            System.out.println("Verified at least f+1 signatures, received " + signatureCount + " signatures");
                                            System.out.println("Transaction status: "+((clientReply.isApproved())?"Approved":"Failed"));
                                            System.out.println("Final balance = $" + clientReply.getFinalBalance() + "\nAfter executing "+ ((clientReply.isApproved())?"approved":"failed") +" transaction $" + request.getAmount() + " : " + request.getClientId() + " -> " + request.getReceiverId());
                                            if(apiConfig.getCurrentView() != clientReply.getCurrentView()){
                                                System.out.println("View changed, current view: "+ clientReply.getCurrentView());
                                            }
                                            apiConfig.setCurrentView((int)clientReply.getCurrentView());
                                        } else {
                                            System.out.println("Received unverified reply for transaction " + request.getAmount() + " : " + request.getClientId() + " -> " + request.getReceiverId()+ " with signature count = "+signatureCount);
                                        }
                                    }

                                }
                            }
                            else {
                                log.warn("Invalid command");
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
