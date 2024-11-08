package com.lab.pbft.controller;

import com.lab.pbft.networkObjects.communique.ServerStatusUpdate;
import com.lab.pbft.service.SocketService;
import com.lab.pbft.util.ServerStatusUtil;
import com.lab.pbft.util.SocketMessageUtil;
import com.lab.pbft.wrapper.AckMessageWrapper;
import com.lab.pbft.wrapper.MessageWrapper;
import com.lab.pbft.util.ServerStatusUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/server")
@Slf4j
public class ServerController {

    @Autowired
    private ServerStatusUtil serverStatusUtil;

    @Autowired
    private SocketService socketService;

    @Autowired
    private SocketMessageUtil socketMessageUtil;

    @GetMapping("/test")
    public ResponseEntity<Boolean> test() {
//        if(serverStatusUtil.isFailed()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        return ResponseEntity.ok(true);
    }

    @GetMapping("/fail")
    public ResponseEntity<Boolean> failServer(@RequestParam(required = false) Integer port, @RequestParam(required = false, defaultValue = "false") Boolean failed, @RequestParam(required = false, defaultValue = "false") Boolean byzantine){

//        if(serverStatusUtil.isFailed()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();

        if(port == null){

            serverStatusUtil.setFailed(failed);
            serverStatusUtil.setByzantine(byzantine);

            log.info("{}: Server failed={}, infected={}", socketService.getAssignedPort(), failed, byzantine);
            return (failed)?ResponseEntity.ok(serverStatusUtil.isFailed()):ResponseEntity.ok(serverStatusUtil.isByzantine());
        }
        else if(port == socketService.getAssignedPort()){

            serverStatusUtil.setFailed(failed);
            serverStatusUtil.setByzantine(byzantine);

            log.info("{}: Server failed={}, infected={}", socketService.getAssignedPort(), failed, byzantine);
            return (failed)?ResponseEntity.ok(serverStatusUtil.isFailed()):ResponseEntity.ok(serverStatusUtil.isByzantine());
        }
        else{
            try{
                log.info("Sending fail/infect message to server {}", port);

                ServerStatusUpdate serverStatusUpdate = ServerStatusUpdate.builder()
                        .failServer(failed)
                        .byzantineServer(byzantine)
                        .build();

                MessageWrapper socketMessageWrapper = MessageWrapper.builder()
                        .type(MessageWrapper.MessageType.SERVER_STATUS_UPDATE)
                        .serverStatusUpdate(serverStatusUpdate)
                        .fromPort(socketService.getAssignedPort())
                        .toPort(port)
                        .build();

                AckMessageWrapper ackMessageWrapper = socketMessageUtil.sendMessageToServer(port, socketMessageWrapper);

                return (failed)?ResponseEntity.ok(ackMessageWrapper.getAckServerStatusUpdate().isServerFailed()):
                        ResponseEntity.ok(ackMessageWrapper.getAckServerStatusUpdate().isServerByzantine());

            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
        }
    }

    @GetMapping("/resume")
    public ResponseEntity<Boolean> resumeServer(@RequestParam(required = false) Integer port, @RequestParam(required = false, defaultValue = "false") Boolean failed, @RequestParam(required = false, defaultValue = "false") Boolean byzantine){
        if(port == null){

            serverStatusUtil.setFailed(failed);
            serverStatusUtil.setByzantine(byzantine);

            log.info("{}: Server failed={}, infected={}", socketService.getAssignedPort(), failed, byzantine);
            return (!failed)?ResponseEntity.ok(!serverStatusUtil.isFailed()):ResponseEntity.ok(!serverStatusUtil.isByzantine());
        }
        else if(port == socketService.getAssignedPort()){

            serverStatusUtil.setFailed(failed);
            serverStatusUtil.setByzantine(byzantine);

            log.info("{}: Server failed={}, infected={}", socketService.getAssignedPort(), failed, byzantine);
            return (!failed)?ResponseEntity.ok(!serverStatusUtil.isFailed()):ResponseEntity.ok(!serverStatusUtil.isByzantine());
        }
        else{

//            if(serverStatusUtil.isFailed()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();

            try{
                ServerStatusUpdate serverStatusUpdate = ServerStatusUpdate.builder()
                        .failServer(failed)
                        .byzantineServer(byzantine)
                        .build();

                MessageWrapper socketMessageWrapper = MessageWrapper.builder()
                        .type(MessageWrapper.MessageType.SERVER_STATUS_UPDATE)
                        .serverStatusUpdate(serverStatusUpdate)
                        .fromPort(socketService.getAssignedPort())
                        .toPort(port)
                        .build();

                AckMessageWrapper ackMessageWrapper = socketMessageUtil.sendMessageToServer(port, socketMessageWrapper);
                log.info("Sending resume/disinfect message to server {}", port);
                return (!failed)?ResponseEntity.ok(!ackMessageWrapper.getAckServerStatusUpdate().isServerFailed()):
                        ResponseEntity.ok(!ackMessageWrapper.getAckServerStatusUpdate().isServerByzantine());
            }
            catch (IOException e){
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
        }
    }
}