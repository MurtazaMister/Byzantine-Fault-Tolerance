package com.lab.pbft.util;

import com.lab.pbft.service.ExitService;
import com.lab.pbft.service.client.ApiService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@Getter
@Setter
public class ServerStatusUtil {
    @Autowired
    @Lazy
    private PortUtil portUtil;

    private boolean failed = false;
    @Autowired
    private RestTemplate restTemplate;
    @Value("${rest.server.url}")
    private String restServerUrl;
    @Value("${rest.server.offset}")
    private String offset;
    @Autowired
    @Lazy
    private ExitService exitService;
    @Autowired
    private ApiService apiService;

    public void setFailed(boolean failed) {
        boolean previousFailed = this.failed;
        this.failed = failed;
    }

    public int getActiveServer(){
        List<Integer> portsArray = portUtil.portPoolGenerator();
        int finalPort = -1;

        for(int port : portsArray) {
            try{
                String url = restServerUrl+":"+Integer.toString(port+Integer.parseInt(offset))+"/server/test";
                boolean up = restTemplate.getForObject(UriComponentsBuilder.fromHttpUrl(url).toUriString(), Boolean.class);
                if(up) {
                    finalPort = port;
                    break;
                }
            }
            catch(Exception e){
                continue;
            }
        }

        if(finalPort == -1){
            log.error("No servers available");
            exitService.exitApplication(0);
        }

        return finalPort+Integer.parseInt(offset);
    }

    public void setServerStatuses(List<Integer> serverIds){ // 2, 3, 5
        List<Integer> portsArray = portUtil.portPoolGenerator(); // 8081, 8082, 8083, 8084, 8085
        List<Boolean> portStatusArray = new ArrayList<>();

        for(int port : portsArray) {
            portStatusArray.add(false);
        }

        for(int id : serverIds){
            portStatusArray.set(id-1, true);
        }

        int activePort = getActiveServer();
        int activeSocketPort = activePort - Integer.parseInt(offset);

        String failUrl = restServerUrl+":"+activePort+"/server/fail";
        String resumeUrl = restServerUrl+":"+activePort+"/server/resume";

        int activeId = -1;

        for(int i = 0;i<portStatusArray.size();i++){
            if(portsArray.get(i) != activeSocketPort){
                if(portStatusArray.get(i)){
                    // true - resume server
                    apiService.resumeServer(portsArray.get(i), resumeUrl);
                }
                else{
                    // false - fail server
                    apiService.failServer(portsArray.get(i), failUrl);
                }
            }
            else{
                activeId = i;
            }
        }

        if(!portStatusArray.get(activeId)){
            apiService.failServer(activeSocketPort, failUrl);
        }
        else{
            apiService.resumeServer(activeSocketPort, resumeUrl);
        }

        log.warn("Setting server statuses, true = active, false = fail");
        log.warn("{}",portsArray);
        log.warn("{}",portStatusArray);

    }

}
