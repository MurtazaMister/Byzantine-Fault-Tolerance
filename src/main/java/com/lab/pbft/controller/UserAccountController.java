package com.lab.pbft.controller;

import com.lab.pbft.config.KeyConfig;
import com.lab.pbft.config.client.ApiConfig;
import com.lab.pbft.dto.ValidateUserDTO;
import com.lab.pbft.model.primary.Log;
import com.lab.pbft.model.primary.ReplyLog;
import com.lab.pbft.model.primary.UserAccount;
import com.lab.pbft.networkObjects.acknowledgements.ClientReply;
import com.lab.pbft.networkObjects.acknowledgements.Reply;
import com.lab.pbft.networkObjects.communique.Request;
import com.lab.pbft.networkObjects.communique.ViewChange;
import com.lab.pbft.repository.primary.LogRepository;
import com.lab.pbft.repository.primary.ReplyLogRepository;
import com.lab.pbft.repository.primary.UserAccountRepository;
import com.lab.pbft.service.PbftService;
import com.lab.pbft.service.SocketService;
import com.lab.pbft.util.ConverterUtil.ByteStringConverter;
import com.lab.pbft.util.PortUtil;
import com.lab.pbft.util.ServerStatusUtil;
import com.lab.pbft.util.SocketMessageUtil;
import com.lab.pbft.util.Stopwatch;
import com.lab.pbft.wrapper.AckMessageWrapper;
import com.lab.pbft.wrapper.MessageWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserAccountController {

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    ServerStatusUtil serverStatusUtil;

    @Autowired
    @Lazy
    private KeyConfig keyConfig;

    @Autowired
    @Lazy
    private PbftService pbftService;
    @Autowired
    @Lazy
    private SocketService socketService;
    @Autowired
    private SocketMessageUtil socketMessageUtil;
    @Autowired
    private ApiConfig apiConfig;
    @Autowired
    private PortUtil portUtil;
    @Autowired
    @Lazy
    private ReplyLogRepository replyLogRepository;

    @Value("${socket.pbft.connection.timeout}")
    private int pbftConnectionTimeout;

    @Value("${socket.pbft.read.timeout}")
    private int pbftReadTimeout;
    @Autowired
    @Lazy
    private LogRepository logRepository;

    @GetMapping("/getId")
    @Transactional
    public ResponseEntity<Long> getUserIdByUsername(@RequestParam String username) {

//        if(serverStatusUtil.isFailed()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();

        log.info("getUserIdByUsername(username) called with username: {}", username);

        Optional<UserAccount> optionalUserAccount = userAccountRepository.findByUsername(username);
        if(optionalUserAccount.isPresent()) {
            return ResponseEntity.ok(optionalUserAccount.get().getId());
        }
        return ResponseEntity.notFound().build();

    }

    @PostMapping("/validate")
    public ResponseEntity<Long> validateUser(@RequestBody ValidateUserDTO bodyUserAccount) {

//        if(serverStatusUtil.isFailed()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();

        log.info("validateUser(userAccount) called with username: {}", bodyUserAccount.getUsername());

        Optional<UserAccount> optionalUserAccount = userAccountRepository.findByUsername(bodyUserAccount.getUsername());

        if(optionalUserAccount.isPresent()){

            UserAccount userAccount = optionalUserAccount.get();

            if(userAccount.getPassword().equals(bodyUserAccount.getPassword())){
                return ResponseEntity.ok(userAccount.getId());
            }

        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(-1L);
    }

    @GetMapping("/balance")
    public ResponseEntity<Long> balanceCheck(@RequestParam Long userId){

//        if(serverStatusUtil.isFailed()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();

        log.info("balanceCheck(userId) called with id: {}", userId);

        UserAccount userAccount = userAccountRepository.findById(userId).orElse(null);

        if(userAccount != null){
            return ResponseEntity.ok(userAccount.getBalance());
        }

        return ResponseEntity.notFound().build();

    }

    @PostMapping("/request")
    public ResponseEntity<ClientReply> transact(@RequestBody Request request) {

        if(serverStatusUtil.isFailed() || serverStatusUtil.isViewChangeTransition()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();

        LocalDateTime startTime = LocalDateTime.now();
        try {

            // Verify request
            if(request.getClientId() == request.getReceiverId() || !request.verifyMessage(keyConfig.getPublicKeyStore().get(request.getClientId()))){
                LocalDateTime currentTime = LocalDateTime.now();
                log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Transaction"));
                return ResponseEntity.badRequest().build();
            }

            ReplyLog replyLog = replyLogRepository.findById(request.getTimestamp()).orElse(null);
            if(replyLog != null){
                ClientReply clientReply = ClientReply.builder()
                        .currentView(-1)
                        .build();
                return ResponseEntity.ok(clientReply);
            }

            UserAccount receiver = userAccountRepository.findById(request.getReceiverId()).orElse(null);
            if(receiver == null){
                LocalDateTime currentTime = LocalDateTime.now();
                log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Transaction"));
                return ResponseEntity.badRequest().build();
            }

            if(request.getCurrentView() != socketService.getCurrentView()) {
                log.warn("Client sending from incorrect view, correcting view");
                request.setCurrentView(socketService.getCurrentView());
            }

            if(socketService.getLeader(request.getCurrentView()) != socketService.getAssignedPort()){
                int toPort = socketService.getCurrentLeader();
                MessageWrapper messageWrapper = MessageWrapper.builder()
                        .type(MessageWrapper.MessageType.REQUEST)
                        .fromPort(socketService.getAssignedPort())
                        .toPort(toPort)
                        .request(request)
                        .build();

                AckMessageWrapper ack = null;
                ack = socketMessageUtil.sendMessageToServer(toPort, messageWrapper, pbftConnectionTimeout, pbftReadTimeout);

                // WAIT FOR REPLY AND SEND TO CLIENT WHO'S WAITING

                // START TIMER HERE FOR THE REQUEST SENT ABOVE
                // IF THE REPLY DOESN'T COME SOON
                // START VIEW CHANGE

                LocalDateTime currentTime = LocalDateTime.now();
                log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Transaction"));

                if(ack != null){
                    if(ack.verifyMessage(keyConfig.getPublicKeyStore().get(ack.getFromPort()))){
                        return ResponseEntity.ok(ack.getClientReply());
                    }
                    else {
                        log.error("Invalid clientRequest received from leader, returning null");
                        return null;
                    }
                }
                else{
                    // No reply from leader, start VIEW CHANGE
                    log.error("Begin view change, unstable leader detected");
                    return null;
                }
            }
            else {
                log.info("Received verified request: {} : {} -> {}", request.getAmount(), request.getClientId(), request.getReceiverId());

                ClientReply clientReply = pbftService.prePrepare(request);

                LocalDateTime currentTime = LocalDateTime.now();
                log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Transaction"));

                return ResponseEntity.ok(clientReply);

            }


        } catch (Exception e) {
            log.error("Exception in transact: {}", e.getMessage());
            LocalDateTime currentTime = LocalDateTime.now();
            log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Transaction"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

    }

    @PostMapping("/rerequest")
    public ResponseEntity<Reply> retransact(@RequestBody Request request) {
        if(serverStatusUtil.isFailed() || serverStatusUtil.isViewChangeTransition()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        LocalDateTime startTime = LocalDateTime.now();
        try {

            ReplyLog previousReply = replyLogRepository.findById(request.getTimestamp()).orElse(null);

            if (previousReply == null) {
                log.info("Reply not found, leader byzantine/down, view change");

                // VIEW CHANGE
                // EXECUTE THE REQUEST AGAIN
                // AFTER VIEW CHANGE

                // Constructing a view change message
                pbftService.viewChange();

                // If i'm the new leader, execute the retransact request
                // else forward to new leader to execute the retransact request
                // chances are, leader is not changed, to take even current view as new view

                // VIEW CHANGED
                log.info("View changed to {}", socketService.getCurrentView());

                // If I'm the new leader, I process the transaction
                if(socketService.getAssignedPort() == socketService.getCurrentLeader()) {
                    log.info("Executing re-transact request");
                    ClientReply clientReply = transact(request).getBody();
                }
                else {
                    int tries = (pbftReadTimeout)/500;

                    while(tries-->0){
                        if(!replyLogRepository.existsByTimestamp(request.getTimestamp())) {
                            log.info("Sleeping until reply gets available");
                            Thread.sleep(500);
                        }
                        else{
                            break;
                        }
                    }
                }

                if(replyLogRepository.existsByTimestamp(request.getTimestamp())) {
                    return ResponseEntity.ok(replyLogRepository.findByTimestamp(request.getTimestamp()).getReply());
                }

                return null;

            } else {

                LocalDateTime currentTime = LocalDateTime.now();
                log.info("{}", Stopwatch.getDuration(startTime, currentTime, "ReTransaction"));

                return ResponseEntity.ok(previousReply.getReply());

            }
        } catch (Exception e) {
            log.error("Exception in retransact: {}", e.getMessage());
        }
        return ResponseEntity.ok(null);
    }
}