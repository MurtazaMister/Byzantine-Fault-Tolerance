package com.lab.pbft.controller;

import com.lab.pbft.config.KeyConfig;
import com.lab.pbft.config.client.ApiConfig;
import com.lab.pbft.dto.ValidateUserDTO;
import com.lab.pbft.model.primary.ReplyLog;
import com.lab.pbft.model.primary.UserAccount;
import com.lab.pbft.networkObjects.acknowledgements.ClientReply;
import com.lab.pbft.networkObjects.acknowledgements.Reply;
import com.lab.pbft.networkObjects.communique.Request;
import com.lab.pbft.repository.primary.ReplyLogRepository;
import com.lab.pbft.repository.primary.UserAccountRepository;
import com.lab.pbft.service.PbftService;
import com.lab.pbft.service.SocketService;
import com.lab.pbft.util.ConverterUtil.ByteStringConverter;
import com.lab.pbft.util.PortUtil;
import com.lab.pbft.util.ServerStatusUtil;
import com.lab.pbft.util.SocketMessageUtil;
import com.lab.pbft.util.Stopwatch;
import com.lab.pbft.wrapper.MessageWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
        LocalDateTime startTime = LocalDateTime.now();
        try {

            // Verify request
            if(request.getClientId() == request.getReceiverId() || !request.verifyMessage(keyConfig.getPublicKeyStore().get(request.getClientId()))){
                LocalDateTime currentTime = LocalDateTime.now();
                log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Transaction"));
                return ResponseEntity.badRequest().build();
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

                socketMessageUtil.sendMessageToServer(toPort, messageWrapper);

                log.info("Sent message to incorrect leader, forwarding");

                // WAIT FOR REPLY AND SEND TO CLIENT WHO'S WAITING

                // START TIMER HERE FOR THE REQUEST SENT ABOVE
                // IF THE REPLY DOESN'T COME SOON
                // START VIEW CHANGE

                LocalDateTime currentTime = LocalDateTime.now();
                log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Transaction"));

                return ResponseEntity.ok(null);

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
        LocalDateTime startTime = LocalDateTime.now();
        try {

            ReplyLog previousReply = replyLogRepository.findById(request.getTimestamp()).orElse(null);

            if (previousReply == null) {
                log.info("Reply not found, leader infected/down, view change");

                // VIEW CHANGE
                // EXECUTE THE REQUEST AGAIN
                // AFTER VIEW CHANGE

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