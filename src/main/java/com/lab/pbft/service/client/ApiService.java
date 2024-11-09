package com.lab.pbft.service.client;


import com.lab.pbft.config.client.ApiConfig;
import com.lab.pbft.dto.ValidateUserDTO;
import com.lab.pbft.model.primary.Log;
import com.lab.pbft.networkObjects.acknowledgements.ClientReply;
import com.lab.pbft.networkObjects.acknowledgements.Reply;
import com.lab.pbft.networkObjects.communique.Request;
import com.lab.pbft.repository.primary.LogRepository;
import com.lab.pbft.util.PortUtil;
import com.lab.pbft.wrapper.AckMessageWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApiService {

    @Value("${rest.server.offset}")
    private int offset;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ApiConfig apiConfig;

    @Autowired
    private PortUtil portUtil;

    @Value("${rest.server.url}")
    private String restServerUrl;

    @Value("${client.request.timeout}")
    private int clientRequestTimeout;

    @Autowired
    @Lazy
    private ClientService clientService;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public ClientReply transact(Request request) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(clientRequestTimeout);
        requestFactory.setReadTimeout(clientRequestTimeout);
        RestTemplate timedRestTemplate = new RestTemplate(requestFactory);

        String url = apiConfig.getRestServerUrlWithPort()+"/user/request";
        log.info("Sending req: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Request> httpRequest = new HttpEntity<>(request, headers);

        try{
            ResponseEntity<ClientReply> response = timedRestTemplate.exchange(url, HttpMethod.POST, httpRequest, ClientReply.class);

            if(response.getStatusCode() == HttpStatus.OK){
                return response.getBody();
            }
        }
        catch (ResourceAccessException e) {
            log.warn("Timeout occured, broadcasting message");

            return ClientReply.builder()
                    .currentView(-1)
                    .build();

        }
        catch (HttpServerErrorException e) {
            // Byzantine server | Failed server
            if(e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE){
                log.error("Service unavailable : {}", e.getMessage());
                return ClientReply.builder()
                        .currentView(-1)
                        .build();
            } else {
                log.error("Server error: {}", e.getMessage());
            }
        }
        catch (HttpClientErrorException e){
            log.error(e.getMessage());
        }
        catch(Exception e){
            log.trace(e.getMessage());
        }
        return ClientReply.builder()
                .currentView(-1)
                .build();
    }

    public List<AckMessageWrapper> retransact(Request request)  {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Request> httpRequest = new HttpEntity<>(request, headers);

        try{

            List<CompletableFuture<AckMessageWrapper>> futures = portUtil.portPoolGenerator().stream()
                    .map(port -> CompletableFuture.supplyAsync(() -> {
                        try{

                            String url = apiConfig.getRestServerUrl()+":"+(port+offset)+"/user/rerequest";
                            log.info("Sending req: {}", url);

                            ResponseEntity<Reply> response = restTemplate.exchange(url, HttpMethod.POST, httpRequest, Reply.class);

                            if(response.getStatusCode() == HttpStatus.OK){
                                return AckMessageWrapper.builder()
                                        .reply(response.getBody())
                                        .fromPort(port)
                                        .build();
                            }

                            return null;

                        }
                        catch(Exception e){
                            log.error("Failure with message to port {}: {}", port, e.getMessage());
                            return null;
                        }
                    }))
                    .toList();

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream()
                            .map(CompletableFuture::join)
                            .filter(reply -> reply!=null)
                            .collect(Collectors.toList())).get();


        }
        catch (ResourceAccessException e) {
            log.warn("Timeout occurred while broadcasting message");
            // !!!!!!!!!
            // !!!!!!!!!
            // !!!!!!!!!
            // !!!!!!!!!
            // !!!!!!!!!
        }
        catch (HttpServerErrorException e) {
            log.error(e.getMessage());
        }
        catch (HttpClientErrorException e){
            log.error(e.getMessage());
        }
        catch(Exception e){
            log.trace(e.getMessage());
        }
        return null;
    }

    // validating a client with userId and password
    public long validate(String username, String password){
        ValidateUserDTO validateUserDTO = new ValidateUserDTO(username, password);
        String url = apiConfig.getRestServerUrlWithPort()+"/user/validate";

        log.info("Sending req: {}", url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ValidateUserDTO> request = new HttpEntity<>(validateUserDTO, headers);

        try{
            ResponseEntity<Long> response = restTemplate.exchange(url, HttpMethod.POST, request, Long.class);

            if(response.getStatusCode() == HttpStatus.OK){
                return (response.getBody()!=null)?response.getBody():-1;
            }
//            else if(response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE){
//                log.error("{}: Service Unavailable", response.getStatusCode());
//                return false;
//            }
        } catch (HttpServerErrorException e) {
            if(e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE){
                log.error("Service unavailable : {}", e.getMessage());
            } else {
                log.error("Server error: {}", e.getMessage());
            }
        } catch (HttpClientErrorException e) {
            log.error("Client error: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Other error: {}", e.getMessage());
        }
        return -1;
    }

    public void byzantineServer(Integer port, String url){
        log.info("Sending req: {} {}", url, (port!=null)?" for port "+port:"");
        Boolean byzantine = false;

        try{
            if(port == null || port.equals(apiConfig.getApiPort())) byzantine = restTemplate.getForObject(UriComponentsBuilder.fromHttpUrl(url).queryParam("byzantine",true).toUriString(), Boolean.class);
            else byzantine = restTemplate.getForObject(UriComponentsBuilder.fromHttpUrl(url).queryParam("port", port).queryParam("byzantine", true).toUriString(), Boolean.class);

            log.info("Server at port {}'s status = {}", (port!=null)?port:(apiConfig.getApiPort()-offset), (byzantine)?"infected":"disinfected");
        }
        catch (HttpServerErrorException e) {
            if(e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE){
                log.error("Service unavailable : {}", e.getMessage());
            } else {
                log.error("Server error: {}", e.getMessage());
            }
        }
        catch (HttpClientErrorException e){
            log.error(e.getMessage());
        }
        catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public void byzantineServer(Integer port){
        String url = apiConfig.getRestServerUrlWithPort()+"/server/fail";
        byzantineServer(port, url);
    }

    public void failServer(Integer port, String url){
        log.info("Sending req: {} {}", url, (port!=null)?" for port "+port:"");
        Boolean failed = false;

        try{
            if(port == null || port.equals(apiConfig.getApiPort())) failed = restTemplate.getForObject(UriComponentsBuilder.fromHttpUrl(url).queryParam("failed", true).toUriString(), Boolean.class);
            else failed = restTemplate.getForObject(UriComponentsBuilder.fromHttpUrl(url).queryParam("port", port).queryParam("failed", true).toUriString(), Boolean.class);

            log.info("Server at port {}'s status = {}", (port!=null)?port:(apiConfig.getApiPort()-offset), (failed)?"failed":"up & running");
        }
        catch (HttpServerErrorException e) {
            if(e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE){
                log.error("Service unavailable : {}", e.getMessage());
            } else {
                log.error("Server error: {}", e.getMessage());
            }
        }
        catch (HttpClientErrorException e){
            log.error(e.getMessage());
        }
        catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public void failServer(Integer port){
        String url = apiConfig.getRestServerUrlWithPort()+"/server/fail";
        failServer(port, url);
    }

    public void resumeServer(Integer port, String url){
        log.info("Sending req: {} {}", url, (port!=null)?" for port "+port:"");
        Boolean resumed = false;

        try{
            if(port == null) resumed = restTemplate.getForObject(UriComponentsBuilder.fromHttpUrl(url).toUriString(), Boolean.class);
            else resumed = restTemplate.getForObject(UriComponentsBuilder.fromHttpUrl(url).queryParam("port", port).toUriString(), Boolean.class);

            log.info("Server at port {}'s status = {}", (port!=null)?port:(apiConfig.getApiPort()-offset), (!resumed)?"failed":"up & running");
        }
        catch (HttpServerErrorException e) {
            if(e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE){
                log.error("Service unavailable : {}", e.getMessage());
            } else {
                log.error("Server error: {}", e.getMessage());
            }
        }
        catch (HttpClientErrorException e){
            log.error(e.getMessage());
        }
        catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public void resumeServer(Integer port){
        String url = apiConfig.getRestServerUrlWithPort()+"/server/resume";
        resumeServer(port, url);
    }

    public Long balanceCheck(String username) {
        Long id = clientService.getId(username);

        List<Integer> portsArray = portUtil.portPoolGenerator();

        int respectivePort = portsArray.get(0) + offset + Math.toIntExact(id) - 1;

        String url = restServerUrl+":"+respectivePort+"/user/balance";

        return balanceCheck(id, url);
    }

    // balance check
    public Long balanceCheck(Long id) {
        String url = apiConfig.getRestServerUrlWithPort()+"/user/balance";
        return balanceCheck(id, url);
    }

    public Long balanceCheck(Long id, String url){
        log.info("Sending req: {}", url);

        Long balance = null;

        try{
            balance = restTemplate.getForObject(UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("userId", id)
                    .toUriString(), Long.class);
            return Long.parseLong(Long.toString(balance));
        }
        catch (HttpServerErrorException e) {
            if(e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE){
                log.error("Service unavailable : {}", e.getMessage());
            } else {
                log.error("Server error: {}", e.getMessage());
            }
        }
        catch (HttpClientErrorException e) {
            log.error("Http error while fetching balance: {}", e.getStatusCode());
        }
        catch (ResourceAccessException e) {
            log.error("Could not access server: {}", e.getMessage());
        }
        catch (Exception e) {
            log.error("{}", e.getMessage());
        }

        return null;
    }

    public List<Log> getLogs(Integer port){
        String url;
        if(port == null) url = apiConfig.getRestServerUrlWithPort()+"/server/logs";
        else {
            url = apiConfig.getRestServerUrl() + ":" + (port) + "/server/logs";
        }

        List<Log> logs;

        try{
            logs = restTemplate.exchange(UriComponentsBuilder.fromHttpUrl(url)
                    .toUriString(), HttpMethod.GET, null, new ParameterizedTypeReference<List<Log>>() {}
            ).getBody();
            return logs;
        }
        catch (HttpServerErrorException e) {
            if(e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE){
                log.error("Service unavailable : {}", e.getMessage());
            } else {
                log.error("Server error: {}", e.getMessage());
            }
        }
        catch (HttpClientErrorException e) {
            log.error("Http error while fetching balance: {}", e.getStatusCode());
        }
        catch (ResourceAccessException e) {
            log.error("Could not access server: {}", e.getMessage());
        }
        catch (Exception e) {
            log.error("{}", e.getMessage());
        }

        return null;

    }

    public List<List<Long>> getAllBalances(){
        List<Integer> ports = portUtil.portPoolGenerator();
        ports.replaceAll(integer -> integer + offset);

        List<List<Long>> balances = new ArrayList<>();

        String url;
        for(int port : ports){
            url = apiConfig.getRestServerUrl() + ":" + (port) + "/user/balances";

        try{
            balances.add(restTemplate.exchange(UriComponentsBuilder.fromHttpUrl(url)
                    .toUriString(), HttpMethod.GET, null, new ParameterizedTypeReference<List<Long>>() {}
            ).getBody());
        }
        catch (HttpServerErrorException e) {
            if(e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE){
                log.error("Service unavailable : {}", e.getMessage());
            } else {
                log.error("Server error: {}", e.getMessage());
            }
        }
        catch (HttpClientErrorException e) {
            log.error("Http error while fetching balance: {}", e.getStatusCode());
        }
        catch (ResourceAccessException e) {
            log.error("Could not access server: {}", e.getMessage());
        }
        catch (Exception e) {
            log.error("{}", e.getMessage());
        }

        }

        return balances;
    }
}